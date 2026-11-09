# Consumable stacking

Seventeen foods, drinks and potions that held one per slot now stack to 1,000, like other food and potions.
Examples: Fancy Club Beverage, Sweet Solorius Cocktail, Zest Tea, Vinna Juice, the Banquet Foods, the Camp
Picnics, Crucible Hero's Draft and Fine Bracing Water. Event, QA/test, NPC, time-limited and Infinite items stay
as they were, and so does Taloc Fruit (an instance item with 1,000 uses).

Each item gets the same change on both sides:

| | Server `item_templates.xml` | Client `Data/Items/Items.pak` → `client_items_misc.xml` |
|---|---|---|
| Stack size | `max_stack_count="1000"` | `max_stack_count` 1000 |
| Splitting | mask bit `CAN_SPLIT` set | `can_split` TRUE |
| Own only one | mask bit `LIMIT_ONE` cleared | `lore` FALSE |

```
python client-mods/consumable-stacking/prepare.py server
python client-mods/consumable-stacking/prepare.py client <output folder>
python client-mods/consumable-stacking/prepare.py check [Items.pak]
```

`client` patches the client's installed `Items.pak` (other mods have already changed it), changes only those
fields, checks that every other archive member is byte-identical, and writes `receipt.json` with both hashes.
Copy the output over the client with Aion closed. `check` fails if any item differs between server and client.
To reverse: restore the original `Items.pak` and `git checkout` the 17 template lines.
