-- Enums
create type public.input_method as enum ('text', 'voice_to_text');
create type public.growth_stage as enum ('seedling', 'sapling', 'mature');
create type public.plant_health as enum ('healthy', 'thirsty', 'wilting');
create type public.item_category as enum ('seed', 'decor', 'backdrop');
create type public.item_rarity as enum ('common', 'uncommon', 'rare', 'epic', 'legendary');
create type public.transaction_type as enum (
  'earned_from_entry',
  'earned_bonus',
  'spent_on_seed',
  'spent_on_decor',
  'spent_on_backdrop',
  'spent_on_bundle',
  'spent_on_water',
  'refund'
);

-- updated_at trigger helper
create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

-- Profile (extends auth.users; one row per Supabase auth user)
create table public.profiles (
  id           uuid primary key references auth.users(id) on delete cascade,
  display_name text not null check (length(trim(display_name)) > 0),
  avatar_key   text,
  level        int  not null default 1 check (level >= 1),
  xp           int  not null default 0 check (xp >= 0),
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

create trigger profiles_set_updated_at
  before update on public.profiles
  for each row execute function public.set_updated_at();

-- Per-user settings
create table public.user_settings (
  user_id                uuid primary key references auth.users(id) on delete cascade,
  reminder_enabled       boolean not null default true,
  reminder_time          time    not null default '08:00',
  reminder_timezone      text    not null default 'UTC',
  sounds_haptics_enabled boolean not null default true,
  theme                  text    not null default 'system' check (theme in ('light','dark','system')),
  daily_entry_cap        int     not null default 10 check (daily_entry_cap > 0),
  updated_at             timestamptz not null default now()
);

create trigger user_settings_set_updated_at
  before update on public.user_settings
  for each row execute function public.set_updated_at();

-- Denormalized counters (kept in sync by RPCs)
create table public.user_stats (
  user_id              uuid primary key references auth.users(id) on delete cascade,
  total_entries        int  not null default 0 check (total_entries >= 0),
  total_coins_earned   int  not null default 0 check (total_coins_earned >= 0),
  plants_grown         int  not null default 0 check (plants_grown >= 0),
  current_streak       int  not null default 0 check (current_streak >= 0),
  longest_streak       int  not null default 0 check (longest_streak >= 0),
  last_entry_date      date,
  updated_at           timestamptz not null default now()
);

create trigger user_stats_set_updated_at
  before update on public.user_stats
  for each row execute function public.set_updated_at();
