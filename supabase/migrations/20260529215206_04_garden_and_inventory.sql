-- One garden per user. Grid size is configurable so we can grow the board over time.
create table public.gardens (
  id                       uuid primary key default extensions.uuid_generate_v4(),
  user_id                  uuid not null unique references auth.users(id) on delete cascade,
  name                     text not null default 'My Garden' check (length(trim(name)) > 0),
  grid_rows                int  not null default 6 check (grid_rows  > 0 and grid_rows  <= 32),
  grid_cols                int  not null default 5 check (grid_cols  > 0 and grid_cols  <= 32),
  active_backdrop_item_id  uuid references public.items(id) on delete set null,
  last_viewed_at           timestamptz,
  created_at               timestamptz not null default now(),
  updated_at               timestamptz not null default now()
);

create trigger gardens_set_updated_at
  before update on public.gardens
  for each row execute function public.set_updated_at();

-- Items the user has unlocked (seeds/decor species; backdrops live here too).
-- Owning a seed/decor lets the user place instances freely.
create table public.user_inventory (
  user_id     uuid not null references auth.users(id) on delete cascade,
  item_id     uuid not null references public.items(id) on delete restrict,
  acquired_at timestamptz not null default now(),
  primary key (user_id, item_id)
);

create index user_inventory_item_idx on public.user_inventory (item_id);

-- Plant instances placed on the garden grid
create table public.garden_plants (
  id              uuid primary key default extensions.uuid_generate_v4(),
  garden_id       uuid not null references public.gardens(id) on delete cascade,
  item_id         uuid not null references public.items(id)   on delete restrict,
  grid_x          int  not null check (grid_x >= 0),
  grid_y          int  not null check (grid_y >= 0),
  growth_stage    public.growth_stage not null default 'seedling',
  health          public.plant_health not null default 'healthy',
  planted_at      timestamptz not null default now(),
  last_watered_at timestamptz,
  updated_at      timestamptz not null default now()
);

create unique index garden_plants_unique_cell
  on public.garden_plants (garden_id, grid_x, grid_y);

create index garden_plants_garden_idx on public.garden_plants (garden_id);
create index garden_plants_item_idx   on public.garden_plants (item_id);

create trigger garden_plants_set_updated_at
  before update on public.garden_plants
  for each row execute function public.set_updated_at();

-- Decoration instances placed on the garden grid
create table public.garden_decor (
  id        uuid primary key default extensions.uuid_generate_v4(),
  garden_id uuid not null references public.gardens(id) on delete cascade,
  item_id   uuid not null references public.items(id)   on delete restrict,
  grid_x    int  not null check (grid_x >= 0),
  grid_y    int  not null check (grid_y >= 0),
  placed_at timestamptz not null default now()
);

create unique index garden_decor_unique_cell
  on public.garden_decor (garden_id, grid_x, grid_y);

create index garden_decor_garden_idx on public.garden_decor (garden_id);
create index garden_decor_item_idx   on public.garden_decor (item_id);

-- Achievements earned by users
create table public.user_achievements (
  user_id        uuid not null references auth.users(id) on delete cascade,
  achievement_id uuid not null references public.achievements(id) on delete cascade,
  earned_at      timestamptz not null default now(),
  primary key (user_id, achievement_id)
);

create index user_achievements_achievement_idx on public.user_achievements (achievement_id);

-- Add the deferred FK on coin_transactions (now that garden_plants exists)
alter table public.coin_transactions
  add constraint coin_transactions_source_garden_plant_id_fkey
  foreign key (source_garden_plant_id)
  references public.garden_plants(id) on delete set null;

create index coin_transactions_source_plant_idx
  on public.coin_transactions (source_garden_plant_id)
  where source_garden_plant_id is not null;

-- Cross-table guard: only seed-category items can be placed as plants,
-- only decor-category items can be placed as decor.
create or replace function public.assert_item_category(item_id uuid, expected public.item_category)
returns void
language plpgsql
as $$
declare
  actual public.item_category;
begin
  select category into actual from public.items where id = item_id;
  if actual is null then
    raise exception 'item % does not exist', item_id;
  end if;
  if actual <> expected then
    raise exception 'item % has category %, expected %', item_id, actual, expected;
  end if;
end;
$$;

create or replace function public.garden_plants_check_category()
returns trigger
language plpgsql
as $$
begin
  perform public.assert_item_category(new.item_id, 'seed');
  return new;
end;
$$;

create trigger garden_plants_check_category_trg
  before insert or update of item_id on public.garden_plants
  for each row execute function public.garden_plants_check_category();

create or replace function public.garden_decor_check_category()
returns trigger
language plpgsql
as $$
begin
  perform public.assert_item_category(new.item_id, 'decor');
  return new;
end;
$$;

create trigger garden_decor_check_category_trg
  before insert or update of item_id on public.garden_decor
  for each row execute function public.garden_decor_check_category();

-- And backdrops on gardens must be backdrop-category
create or replace function public.gardens_check_backdrop_category()
returns trigger
language plpgsql
as $$
begin
  if new.active_backdrop_item_id is not null then
    perform public.assert_item_category(new.active_backdrop_item_id, 'backdrop');
  end if;
  return new;
end;
$$;

create trigger gardens_check_backdrop_category_trg
  before insert or update of active_backdrop_item_id on public.gardens
  for each row execute function public.gardens_check_backdrop_category();
