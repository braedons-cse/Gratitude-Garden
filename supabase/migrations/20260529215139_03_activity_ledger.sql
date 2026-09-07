-- Coin wallet (single row per user)
create table public.coin_wallets (
  user_id    uuid primary key references auth.users(id) on delete cascade,
  balance    int  not null default 0 check (balance >= 0),
  updated_at timestamptz not null default now()
);

create trigger coin_wallets_set_updated_at
  before update on public.coin_wallets
  for each row execute function public.set_updated_at();

-- Gratitude entries (soft delete via deleted_at)
create table public.gratitude_entries (
  id             uuid primary key default extensions.uuid_generate_v4(),
  user_id        uuid not null references auth.users(id) on delete cascade,
  entry_text     text not null check (length(trim(entry_text)) > 0),
  input_method   public.input_method not null,
  coins_awarded  int  not null default 0 check (coins_awarded >= 0),
  entry_date     date not null default (now() at time zone 'UTC')::date,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  deleted_at     timestamptz
);

create index gratitude_entries_user_date_idx
  on public.gratitude_entries (user_id, entry_date desc)
  where deleted_at is null;

create index gratitude_entries_user_created_idx
  on public.gratitude_entries (user_id, created_at desc)
  where deleted_at is null;

create trigger gratitude_entries_set_updated_at
  before update on public.gratitude_entries
  for each row execute function public.set_updated_at();

-- Coin transaction ledger (append-only, signed amounts)
create table public.coin_transactions (
  id                     uuid primary key default extensions.uuid_generate_v4(),
  user_id                uuid not null references auth.users(id) on delete cascade,
  amount                 int  not null,                     -- positive = earned, negative = spent
  type                   public.transaction_type not null,
  balance_after          int  not null check (balance_after >= 0),
  source_entry_id        uuid references public.gratitude_entries(id) on delete set null,
  source_item_id         uuid references public.items(id) on delete set null,
  source_bundle_id       uuid references public.bundles(id) on delete set null,
  source_garden_plant_id uuid, -- FK added in migration 04 (table doesn't exist yet)
  notes                  text,
  created_at             timestamptz not null default now()
);

create index coin_transactions_user_created_idx
  on public.coin_transactions (user_id, created_at desc);

create index coin_transactions_source_entry_idx
  on public.coin_transactions (source_entry_id)
  where source_entry_id is not null;
