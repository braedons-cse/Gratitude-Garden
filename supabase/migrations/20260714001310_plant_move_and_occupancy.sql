-- Reject planting into a cell that's already occupied (users now pick the cell).
create or replace function public.place_plant(p_item_id uuid, p_grid_x integer, p_grid_y integer)
 returns garden_plants
 language plpgsql
 security definer
 set search_path to 'public', 'auth'
as $function$
declare
  v_user   uuid := auth.uid();
  v_garden public.gardens;
  v_owned  boolean;
  v_plant  public.garden_plants;
begin
  if v_user is null then raise exception 'not authenticated' using errcode = '42501'; end if;

  select * into v_garden from public.gardens where user_id = v_user;
  if v_garden.id is null then raise exception 'garden not found'; end if;

  if p_grid_x < 0 or p_grid_x >= v_garden.grid_cols or p_grid_y < 0 or p_grid_y >= v_garden.grid_rows then
    raise exception 'cell (%,%) out of bounds (cols=%, rows=%)', p_grid_x, p_grid_y, v_garden.grid_cols, v_garden.grid_rows;
  end if;

  if exists(
    select 1 from public.garden_plants
    where garden_id = v_garden.id and grid_x = p_grid_x and grid_y = p_grid_y
  ) then
    raise exception 'cell (%,%) is occupied', p_grid_x, p_grid_y;
  end if;

  select exists(select 1 from public.user_inventory where user_id = v_user and item_id = p_item_id) into v_owned;
  if not v_owned then raise exception 'you do not own this seed'; end if;

  perform public.assert_item_category(p_item_id, 'seed');

  insert into public.garden_plants (garden_id, item_id, grid_x, grid_y)
  values (v_garden.id, p_item_id, p_grid_x, p_grid_y)
  returning * into v_plant;

  return v_plant;
end;
$function$;

-- Move an existing plant to a new cell. There is no owner UPDATE policy on
-- garden_plants, so this SECURITY DEFINER function is the only way for a user
-- to reposition their plants; it enforces ownership, bounds, and occupancy.
create or replace function public.move_plant(p_plant_id uuid, p_grid_x integer, p_grid_y integer)
 returns garden_plants
 language plpgsql
 security definer
 set search_path to 'public', 'auth'
as $function$
declare
  v_user   uuid := auth.uid();
  v_garden public.gardens;
  v_plant  public.garden_plants;
begin
  if v_user is null then raise exception 'not authenticated' using errcode = '42501'; end if;

  -- Plant must belong to the caller's own garden.
  select gp.* into v_plant
  from public.garden_plants gp
  join public.gardens g on g.id = gp.garden_id
  where gp.id = p_plant_id and g.user_id = v_user;
  if v_plant.id is null then raise exception 'plant not found'; end if;

  select * into v_garden from public.gardens where id = v_plant.garden_id;

  if p_grid_x < 0 or p_grid_x >= v_garden.grid_cols or p_grid_y < 0 or p_grid_y >= v_garden.grid_rows then
    raise exception 'cell (%,%) out of bounds (cols=%, rows=%)', p_grid_x, p_grid_y, v_garden.grid_cols, v_garden.grid_rows;
  end if;

  if exists(
    select 1 from public.garden_plants
    where garden_id = v_plant.garden_id
      and grid_x = p_grid_x and grid_y = p_grid_y
      and id <> p_plant_id
  ) then
    raise exception 'cell (%,%) is occupied', p_grid_x, p_grid_y;
  end if;

  update public.garden_plants
     set grid_x = p_grid_x, grid_y = p_grid_y, updated_at = now()
   where id = p_plant_id
   returning * into v_plant;

  return v_plant;
end;
$function$;

grant execute on function public.move_plant(uuid, integer, integer) to authenticated;
