-- Items catalog: seeds, decor, backdrops
create table public.items (
  id              uuid primary key default extensions.uuid_generate_v4(),
  slug            text not null unique,
  category        public.item_category not null,
  name            text not null,
  description     text,
  rarity          public.item_rarity not null default 'common',
  price_coins     int  not null check (price_coins >= 0),
  level_required  int  not null default 1 check (level_required >= 1),
  asset_key       text,
  is_purchasable  boolean not null default true,
  is_starter      boolean not null default false,
  available_from  timestamptz,
  available_until timestamptz,
  created_at      timestamptz not null default now()
);

create index items_category_idx          on public.items (category);
create index items_starter_idx           on public.items (is_starter) where is_starter;
create index items_available_window_idx  on public.items (available_from, available_until);

-- Bundles (multi-item SKUs)
create table public.bundles (
  id              uuid primary key default extensions.uuid_generate_v4(),
  slug            text not null unique,
  name            text not null,
  description     text,
  price_coins     int  not null check (price_coins >= 0),
  is_featured     boolean not null default false,
  available_from  timestamptz,
  available_until timestamptz,
  created_at      timestamptz not null default now()
);

create table public.bundle_items (
  bundle_id uuid not null references public.bundles(id) on delete cascade,
  item_id   uuid not null references public.items(id)   on delete restrict,
  quantity  int  not null default 1 check (quantity > 0),
  primary key (bundle_id, item_id)
);

create index bundle_items_item_idx on public.bundle_items (item_id);

-- Achievement catalog
create table public.achievements (
  id          uuid primary key default extensions.uuid_generate_v4(),
  slug        text not null unique,
  name        text not null,
  description text,
  icon_key    text,
  criteria    jsonb not null,   -- e.g. {"type":"streak","value":7} or {"type":"entries","value":100}
  created_at  timestamptz not null default now()
);
