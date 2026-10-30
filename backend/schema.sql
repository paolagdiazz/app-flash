-- BlueWay: avisos compartidos entre móviles.
-- Pensado para Supabase (pegar en SQL Editor), pero solo necesita PostgreSQL 13+ y PostgREST.
--
-- Modelo de seguridad:
--  * La clave pública de la app solo puede ejecutar las cinco funciones de abajo.
--  * Las tablas no son accesibles directamente, y device_id nunca se devuelve.
--  * device_id es un identificador aleatorio de la instalación: no hay cuenta ni datos personales.
--    Se puede falsificar, así que los límites por dispositivo frenan abusos casuales, no ataques
--    dirigidos. Por eso también hay un límite global de altas.

create table if not exists public.bw_reports (
    id            uuid primary key default gen_random_uuid(),
    device_id     uuid not null,
    type          text not null,
    lat           double precision not null,
    lon           double precision not null,
    description   text not null default '',
    created_at    timestamptz not null default now(),
    expires_at    timestamptz not null,
    confirmations integer not null default 1,
    rejections    integer not null default 0
);
create index if not exists bw_reports_expires on public.bw_reports (expires_at);
create index if not exists bw_reports_device on public.bw_reports (device_id, created_at);

create table if not exists public.bw_votes (
    report_id  uuid not null references public.bw_reports (id) on delete cascade,
    device_id  uuid not null,
    positive   boolean not null,
    created_at timestamptz not null default now(),
    primary key (report_id, device_id)
);

alter table public.bw_reports enable row level security;
alter table public.bw_votes enable row level security;
revoke all on public.bw_reports, public.bw_votes from public;
do $$ begin
    if exists (select from pg_roles where rolname = 'anon') then
        revoke all on public.bw_reports, public.bw_votes from anon;
    end if;
    if exists (select from pg_roles where rolname = 'authenticated') then
        revoke all on public.bw_reports, public.bw_votes from authenticated;
    end if;
end $$;

-- Horas de vida por tipo. Coincide con ReportType en la app. Null si el tipo no existe.
create or replace function public.bw_type_hours(p_type text) returns integer
language sql immutable set search_path = pg_catalog as $$
    select case p_type
        when 'WORKS' then 168
        when 'EVENT' then 6
        when 'TRAFFIC' then 2
        when 'NOISE_EXTREME' then 2
        when 'NOISE_HIGH' then 2
        when 'NOISE_MODERATE' then 2
        when 'NOISE_NORMAL' then 2
        when 'NOISE_LOW' then 2
        when 'GENERAL' then 3
    end
$$;

create or replace function public.bw_meters(lat1 double precision, lon1 double precision, lat2 double precision, lon2 double precision)
returns double precision
language sql immutable set search_path = pg_catalog as $$
    select sqrt(power((lat1 - lat2) * 110574.0, 2) + power((lon1 - lon2) * 111320.0 * cos(radians((lat1 + lat2) / 2)), 2))
$$;

drop function if exists public.list_reports(uuid);
create function public.list_reports(p_device uuid)
returns table (
    id uuid, type text, lat double precision, lon double precision, description text,
    created_ms bigint, expires_ms bigint, confirmations integer, rejections integer,
    mine boolean, voted boolean
)
language sql stable security definer set search_path = public, pg_catalog as $$
    select r.id, r.type, r.lat, r.lon, r.description,
           (extract(epoch from r.created_at) * 1000)::bigint,
           (extract(epoch from r.expires_at) * 1000)::bigint,
           r.confirmations, r.rejections,
           r.device_id = p_device,
           exists (select 1 from bw_votes v where v.report_id = r.id and v.device_id = p_device)
    from bw_reports r
    where r.expires_at > now() - interval '1 day'
    order by r.created_at desc
    limit 500
$$;

-- Crea un aviso o, si ya hay uno igual cerca y activo, lo refuerza. Devuelve el id y si se fusionó.
drop function if exists public.create_report(uuid, text, double precision, double precision, text);
create function public.create_report(p_device uuid, p_type text, p_lat double precision, p_lon double precision, p_description text)
returns table (id uuid, merged boolean)
language plpgsql security definer set search_path = public, pg_catalog as $$
declare
    v_hours integer := bw_type_hours(p_type);
    v_lat double precision := round(p_lat::numeric, 4)::double precision;
    v_lon double precision := round(p_lon::numeric, 4)::double precision;
    v_text text := left(trim(regexp_replace(coalesce(p_description, ''), '[[:cntrl:]]+', ' ', 'g')), 120);
    v_existing bw_reports%rowtype;
begin
    if p_device is null then raise exception 'invalid_device'; end if;
    if v_hours is null then raise exception 'invalid_type'; end if;
    -- Zona con datos de la app: Málaga capital.
    if v_lat is null or v_lon is null or v_lat not between 36.685 and 36.755 or v_lon not between -4.515 and -4.345 then
        raise exception 'out_of_area';
    end if;
    if (select count(*) from bw_reports r where r.device_id = p_device and r.created_at > now() - interval '10 minutes') >= 5 then
        raise exception 'rate_limited';
    end if;
    if (select count(*) from bw_reports r where r.created_at > now() - interval '1 minute') >= 60 then
        raise exception 'busy';
    end if;

    select * into v_existing from bw_reports r
    where r.type = p_type and r.expires_at > now()
      and not (r.rejections >= 2 and r.rejections > r.confirmations)
      and bw_meters(r.lat, r.lon, v_lat, v_lon) <= 120
    order by r.created_at desc limit 1;

    if found then
        -- La misma persona no puede reforzar su propio aviso ni hacerlo dos veces.
        if v_existing.device_id <> p_device then
            insert into bw_votes (report_id, device_id, positive) values (v_existing.id, p_device, true)
            on conflict do nothing;
            if found then
                update bw_reports r set confirmations = r.confirmations + 1 where r.id = v_existing.id;
            end if;
        end if;
        return query select v_existing.id, true;
        return;
    end if;

    -- Limpieza de avisos caducados hace más de una semana: no se conservan indefinidamente.
    delete from bw_reports r where r.expires_at < now() - interval '7 days';

    return query
    insert into bw_reports as r (device_id, type, lat, lon, description, expires_at)
    values (p_device, p_type, v_lat, v_lon, v_text, now() + make_interval(hours => v_hours))
    returning r.id, false;
end
$$;

-- Confirma o rechaza un aviso de otra persona. Solo un voto por instalación.
drop function if exists public.vote_report(uuid, uuid, boolean);
create function public.vote_report(p_device uuid, p_id uuid, p_still_there boolean)
returns void
language plpgsql security definer set search_path = public, pg_catalog as $$
declare
    v_owner uuid;
begin
    if p_device is null or p_still_there is null then raise exception 'invalid_device'; end if;
    select r.device_id into v_owner from bw_reports r where r.id = p_id and r.expires_at > now();
    if not found then raise exception 'not_found'; end if;
    if v_owner = p_device then raise exception 'own_report'; end if;
    insert into bw_votes (report_id, device_id, positive) values (p_id, p_device, p_still_there)
    on conflict do nothing;
    if not found then raise exception 'already_voted'; end if;
    if p_still_there then
        -- Confirmar alarga media hora la vida del aviso, sin superar la de su tipo.
        update bw_reports r set
            confirmations = r.confirmations + 1,
            expires_at = greatest(r.expires_at, least(now() + make_interval(hours => bw_type_hours(r.type)), r.expires_at + interval '30 minutes'))
        where r.id = p_id;
    else
        update bw_reports r set rejections = r.rejections + 1 where r.id = p_id;
    end if;
end
$$;

-- Elimina un aviso propio.
drop function if exists public.delete_report(uuid, uuid);
create function public.delete_report(p_device uuid, p_id uuid)
returns void
language plpgsql security definer set search_path = public, pg_catalog as $$
begin
    delete from bw_reports r where r.id = p_id and r.device_id = p_device;
    if not found then raise exception 'not_found'; end if;
end
$$;

revoke all on function public.bw_type_hours(text) from public;
revoke all on function public.bw_meters(double precision, double precision, double precision, double precision) from public;
revoke all on function public.list_reports(uuid) from public;
revoke all on function public.create_report(uuid, text, double precision, double precision, text) from public;
revoke all on function public.vote_report(uuid, uuid, boolean) from public;
revoke all on function public.delete_report(uuid, uuid) from public;

do $$ begin
    if exists (select from pg_roles where rolname = 'anon') then
        revoke all on function public.bw_type_hours(text) from anon;
        revoke all on function public.bw_meters(double precision, double precision, double precision, double precision) from anon;
        grant execute on function public.list_reports(uuid) to anon;
        grant execute on function public.create_report(uuid, text, double precision, double precision, text) to anon;
        grant execute on function public.vote_report(uuid, uuid, boolean) to anon;
        grant execute on function public.delete_report(uuid, uuid) to anon;
    end if;
    -- Supabase da permisos por defecto a "authenticated"; la app no usa ese rol.
    if exists (select from pg_roles where rolname = 'authenticated') then
        revoke all on function public.bw_type_hours(text) from authenticated;
        revoke all on function public.bw_meters(double precision, double precision, double precision, double precision) from authenticated;
        revoke all on function public.list_reports(uuid) from authenticated;
        revoke all on function public.create_report(uuid, text, double precision, double precision, text) from authenticated;
        revoke all on function public.vote_report(uuid, uuid, boolean) from authenticated;
        revoke all on function public.delete_report(uuid, uuid) from authenticated;
    end if;
end $$;
