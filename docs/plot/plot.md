# Plot

The story of Winterheart: what happens, who is involved and how it ends. This is the reference for writing journal entries, titles and structures, and for building the endings. It says, for each part, whether it is already in the game or still planned.

**In short: there is no escape.** Either something catastrophic happens that ruins the whole continent, or you go insane and starve until the cycle ends and you become the wendigo that was hunting you.

Open questions are at the [end](#open-questions). When a question is settled, move the answer into the right section and delete the question.

## Premise

The far north, autumn 1932. The player **is Lt. Kaamos** of the Northern Watch Corps, stationed at a remote cabin. The days get shorter until, on day 50, the night stops ending. Kaamos is caught in a loop: dying sends him back to the morning, and he half-remembers living each day before ("double dreams"). Something in the woods is eating the herds.

"Kaamos" is Finnish for the polar night, so his name is also the thing that ends the game.

## Characters

| Name | Who | Fate | In game |
| --- | --- | --- | --- |
| Lt. Kaamos | The player. Northern Watch Corps officer. Keeps the journal (`journal` book) and wrote the survival manual (`field_notebook`, *Surviving the Northernmost North*). | Either dies in the beacon ending and wakes in the afterlife cabin, or breaks in the bad ending and becomes the wendigo. | Yes (books in the starter kit) |
| Colonel Connors ("Colonel C.") | Kaamos's superior at the watchtower. Had warned Kaamos about the monsters. | Hanged himself off the watchtower's lower deck. Leaves a suicide note (page `suicide_note`). | Yes |
| The ferry crew and its captain | Stranded in a ferry wrecked in the ice to the east. Lived inside the wreck because the forest was too dangerous. | Dead or gone. The captain's log (page `captains_log`) begs whoever reads it to light the beacon. | Yes |
| The wendigo | The thing hunting the herds and Kaamos. Smells of iron, eyes glow like the moon. | It is what Kaamos becomes in the bad ending (see [The wendigo](#the-wendigo)). | Spawns (`antlers:wendigo`); the link to Kaamos is planned |

## Places

Coordinates are from `config/paxi/datapacks/winterheart_structures/data/winterheart/fixed_structures/placements.json`.

| Place | Where | Role |
| --- | --- | --- |
| The cabin | Spawn (starter structure `Spawn_v2.3.schem`) | Kaamos's post. Something is rotting under the floor (guide `some_horrible_smell`). |
| The watchtower | (0, 400), south of spawn | Colonel Connors' post, where he hanged himself. |
| The ferry wreck | (950, 0), east | The crew's last shelter, with the captain's log. |
| The beacon | (−1100, 0), on a summit to the west | Lighting it triggers the catastrophe (see [Endings](#endings)). |
| The south / "the capitol" | South | Where Kaamos means to go to warn the capitol. The intro note says *"Find me down south."* |
| The afterlife cabin | Planned | A dreamlike copy of the cabin in an all-white landscape (see [Beacon ending](#beacon-ending-the-catastrophe)). |

## Timeline

The journal entries (`config/paxi/datapacks/winterheart_guides/data/winterheart/guides/guides.json`, book `journal`) carry the story. Day numbers come from the time system and spawner configs.

| When | Event | Journal entry | In game |
| --- | --- | --- | --- |
| Start | Kaamos opens the cabin door. | `introduction`: *"I have left this home's stewardship to whoever may need its shelter. Find me down south."* | Yes |
| Any time | The smell under the cabin floor. | `some_horrible_smell` | Yes |
| Any time | Looking at a bed: the loop shows through. | `double_dreams`: *"I feel as if I have written this entry a thousand times and more."* | Yes |
| First monster | Kaamos sees what Colonel C. warned about and resolves to report to the watchtower. | `abominations` | Yes |
| Day 11+ | Hostiles start spawning at dusk and night. | none | Yes |
| Day 16, dawn | The herds are disappearing. | `the_livestock` | Yes |
| Day 19, dusk | Growls and screams in the forest. | `unnatural_sounds` | Yes |
| Day 20, after dusk | The wendigo appears for the first time (again on days 30, 35, 40, 43, 46...). | `vile_beast`: *"I must light the beacon. I must warn the capitol. I must..."* | Yes |
| Day 21+ | Hostiles spawn at any time of day. | none | Yes |
| Day 30+ | No new animals are born or spawn. | none | Yes |
| Watchtower visit | Connors is dead; Kaamos is alone. Points him to the ferry in the east. | `a_tragedy` | Yes |
| Ferry visit | The captain's log asks for the beacon to be lit. Kaamos holds off. | `the_ferry` | Yes |
| Day 50 | Eternal night: time stops at midnight, beds refuse, sanity drains. | none | Yes |

## The loop

- **Dying** rewinds to dawn of the same day (MineZero's Return by Death) and shows *"Fall N times, stand up N+1."* with a splash word (FOCUS, SURVIVE, OUTLAST, PERSIST, PROLIFERATE). Implemented in [time-system](../features/time-system.md).
- **Sleeping** passes the day and sets the checkpoint that death returns to.
- The journal is the same one Kaamos keeps rewriting each loop ("a thousand times and more").

Both endings break the loop. The last line of the death message is what tells them apart: the bad ending shows "Fall N times" and never "stand up".

## Endings

Every ending is a loss. The player can choose how to lose, but not whether.

### Beacon ending: the catastrophe

1. Kaamos lights the beacon (as the captain's log begged).
2. Something horrible happens that ruins the whole continent. Current idea: the beacon is part of an early warning system, and lighting it raises a false alarm that launches a series of nuclear missiles. Not final (see [Open questions](#open-questions)).
3. The player dies, whatever the catastrophe is.
4. The player wakes in **the afterlife cabin**:
   - looks like the starting cabin, but fully repaired, with no carcasses outside,
   - the player is in adventure mode and always at full health,
   - the landscape around it is completely white, as if from a dream.

   This shows the afterlife looping straight back to the cabin.

In game: only lighting the beacon. Right-clicking a beacon builds its iron base ([beacon-base](../features/beacon-base.md)). Nothing else is built yet.

### Bad ending: insanity

1. Kaamos's sanity collapses until he can't take it any more. (The midnight drain and the eternal night push the player towards this.)
2. The seppuku animation plays, and the screen gets bloodier by the second.
3. Text like **"WAKE UP, WAKE UP"** flashes on screen.
4. An ominous death screen shows **"Fall N times"**, with no "stand up N+1".
5. This is **permadeath**: the player can't wake up again. The death screen stays, with only two buttons, **Spectate** and **Title screen**, like the hardcore death screen.
6. Kaamos becomes the wendigo (see below).

In game: the seppuku animation plays once at sanity ≤ 10 ([seppuku](../features/seppuku.md)), but it doesn't kill the player. The blood overlay, the "WAKE UP" text, the death screen and permadeath are planned.

### The wendigo

The wendigo is what Kaamos becomes in the bad ending. It closes the circle: the beast hunting the herds and Kaamos is a Kaamos from an earlier loop who broke. The smell under the floor, the vanishing herds and the starvation all point to this.

How the game shows it is undecided. Ideas so far:

- give the wendigo a name close to "Kaamos",
- have the wendigo sometimes disguise itself as the player.

## Motifs

- **The polar night**: Kaamos's name, the shortening days, the eternal night from day 50.
- **"Fall seven times, stand up eight"** (Japanese proverb *nana korobi ya oki*): the death message. Taking away the "stand up" half marks the end of the loop. The seppuku in the bad ending belongs to the same Japanese motif.
- **Hunger and iron**: the wendigo smells of iron (blood); the gore messages say *"The sharp smell of copper and iron fills the air..."* and *"You must ######## it."*
- **Suicide as the end of the watch**: Connors hangs himself; Kaamos's bad ending is seppuku.
- **White**: the snow outside, and the pure white afterlife.

## Open questions

1. **What does lighting the beacon trigger?** The current idea is a false alarm in an early warning system that launches nuclear missiles. Nuclear weapons (1945) and ballistic-missile early warning (late 1950s) didn't exist in 1932. Options: an alternate history, moving the date, or a different catastrophe.
2. **How is the catastrophe shown?** A cutscene, sky effects, explosions over the landscape, or only the death and a message?
3. **Is the afterlife cabin the end?** Can the player explore the white landscape, are there credits, or is it an endless idle state? Is it a separate dimension or built in the same world?
4. **Does "escape south" still exist?** The pack README says the player *"has to escape south before the eternal night"*, the World Painter plans an "escape-south route", and the intro says *"Find me down south."* With no escape, what does going south do? Does Kaamos never arrive, does the south loop back north, or is the capitol already gone? The README line should be updated to match.
5. **Who wrote the introduction note?** If the player is Kaamos, who left the cabin "to whoever may need its shelter" and wrote "Find me down south"? Kaamos from an earlier loop? The wendigo? Or should the note be rewritten?
6. **What happens on day 50 if neither ending has happened?** Does the eternal night lead straight into the bad ending (the drain takes sanity to 0), or is there a third outcome?
7. **What triggers the bad ending exactly?** Sanity ≤ 10 (the current seppuku threshold), sanity 0, or insanity *and* starvation together ("go insane and starve")? Does dying normally many times count?
8. **How is Kaamos tied to the wendigo?** Name, disguise as the player, a skin, behaviour, a journal entry? Does the wendigo met in a normal run imply an earlier Kaamos already broke?
9. **What does Spectate show after permadeath?** In single player, the player watching the world without them, or the wendigo that is now them?
10. **Multiplayer.** If every player is Kaamos, how do the loop, the endings and permadeath work with several players? Does one player's bad ending end the run for everyone?
11. **Colonel Connors.** What did he tell Kaamos about the monsters, and did he know about the beacon? Was he in the loop too?
12. **The crew.** Why did they want the beacon lit? Did they know what it does?
13. **Where is the capitol, and which country?** The Northern Watch Corps is fictional; the setting reads as Finnish or Canadian.
14. **The scrambled gore message** `§0You must §k########§r§0 it.` in `config/paxi/datapacks/winterheart_survival/data/winterheart/proximity/rules.json`: what is the hidden word (eight characters)? "devour" is six letters, "eat" three.
15. **The smell under the cabin floor.** Whose remains are they?
