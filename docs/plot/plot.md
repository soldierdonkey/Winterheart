# Plot

The story of Winterheart: what happens, who is involved and how it ends. This is the reference for writing journal entries, titles and structures, and for building the endings. It says, for each part, whether it is already in the game or still planned.

**In short: there is no escape.** Either something catastrophic happens that ruins the whole continent, or you go insane and starve until the cycle ends and you become the wendigo that was hunting you. There is no escape to the south either: the south holds only the watchtower and Colonel Connors' body.

Open questions are at the [end](#open-questions). When a question is settled, move the answer into the right section and delete the question.

## Premise

The far north. The player **is Lt. Kaamos** of the Northern Watch Corps, stationed at a remote cabin. The days get shorter until, on day 50, the night stops ending. Kaamos is caught in a loop: dying sends him back to the morning, and he half-remembers living each day before ("double dreams"). The world resets, but time does not: he has been in the loop for over six thousand days. Something in the woods is eating the herds.

The journal is dated 1932, but the present is years later, in the nuclear era: see [The year](#the-year).

"Kaamos" is Finnish for the polar night, so his name is also the thing that ends the game.

The story is told from an American perspective, with American customs. Where exactly it takes place stays ambiguous for players: the game never names the country, the capitol or the northern post's location.

Winterheart is **single player only**. The Multiplayer button is hidden from the main menu, and the pack crashes on purpose if launched as a dedicated server. The loop, the endings and permadeath only have to work for one Kaamos. In game: the server crash is done ([singleplayer-only](../features/singleplayer-only.md)), hiding the Multiplayer button is planned.

## Characters

| Name | Who | Fate | In game |
| --- | --- | --- | --- |
| Lt. Kaamos | The player. Northern Watch Corps officer. Keeps the journal (`journal` book) and wrote the survival manual (`field_notebook`, *Surviving the Northernmost North*). A Kaamos from an earlier loop wrote the introduction note. Has been in the loop for over six thousand days. | Either dies in the beacon ending and wakes in the afterlife cabin, or breaks in the bad ending and becomes the wendigo. | Yes (books in the starter kit) |
| Colonel Connors ("Colonel C.") | Kaamos's superior at the watchtower. Had warned Kaamos about the monsters. | Hanged himself off the watchtower's lower deck. Leaves a suicide note (page `suicide_note`). | Yes |
| The ferry crew and its captain | Stranded in a ferry wrecked in the ice to the east. Lived inside the wreck because the forest was too dangerous. The captain watched his crew be terrorized by wendigos and then become them. He wanted to put them out of their misery by triggering the early warning system (the beacon), but was unable to. | Dead or gone. The captain's log (page `captains_log`) begs whoever reads it to light the beacon. He notes that he stayed loyal to his humanity and never ate human flesh. | Log yes; the captain's backstory in it is planned |
| The wendigo | The thing hunting the herds and Kaamos. Smells of iron, eyes glow like the moon. | It is what Kaamos becomes in the bad ending (see [The wendigo](#the-wendigo)). | Spawns (`antlers:wendigo`); the link to Kaamos is planned |

## Places

Coordinates are from `config/paxi/datapacks/winterheart_structures/data/winterheart/fixed_structures/placements.json`.

| Place | Where | Role |
| --- | --- | --- |
| The cabin | Spawn (starter structure `Spawn_v2.3.schem`) | Kaamos's post. Something is rotting under the floor (guide `some_horrible_smell`). |
| The watchtower | (0, 400), south of spawn | Colonel Connors' post, where he hanged himself. The only thing to find "down south". |
| The ferry wreck | (950, 0), east | The crew's last shelter, with the captain's log. |
| The beacon | (−1100, 0), on a summit to the west | Lighting it triggers the catastrophe (see [Endings](#endings)). |
| "The capitol" | Somewhere further south, never reached or named | Where Kaamos wants to send a warning (`vile_beast`). Kept ambiguous on purpose (see [Premise](#premise)). |
| The afterlife cabin | Planned, in its own dimension | A dreamlike copy of the cabin in an all-white landscape (see [Beacon ending](#beacon-ending-the-catastrophe)). |

The World Painter's path (`tools/worldpainter/`), which runs 400 blocks south from spawn, was planned as an escape route. That idea is dropped. It may become the early journey south to the watchtower, where Kaamos finds Connors dead (open question 2).

## Timeline

The journal entries (`config/paxi/datapacks/winterheart_guides/data/winterheart/guides/guides.json`, book `journal`) carry the story. Day numbers come from the time system and spawner configs. They are game days as Kaamos perceives them in the current run, not calendar dates (see [The year](#the-year)).

| When | Event | Journal entry | In game |
| --- | --- | --- | --- |
| Start | Kaamos opens the cabin door and finds a note he wrote in an earlier loop. | `introduction`: *"I have left this home's stewardship to whoever may need its shelter. Find me down south."* | Yes |
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
| Day 50 | Eternal night: time stops at midnight, beds refuse, sanity drains. Dying with low sanity from now on is the bad ending. | none | Eternal night yes; bad ending planned |

## The loop

- **Dying** rewinds to dawn of the same day (MineZero's Return by Death) and shows *"Fall N times, stand up N+1."* with a splash word (FOCUS, SURVIVE, OUTLAST, PERSIST, PROLIFERATE). Implemented in [time-system](../features/time-system.md).
- **Sleeping** passes the day and sets the checkpoint that death returns to.
- **Time moves on** each time Kaamos dies, even though the world resets to the morning (see [The year](#the-year)).
- **Respawning is free** until two conditions are both met: low sanity **and** day 50 (the eternal night). Until then every death is a rewind, however low sanity gets.
- The journal is the same one Kaamos keeps rewriting each loop ("a thousand times and more"). The introduction note was written by Kaamos in an earlier loop.

Both endings break the loop. The last line of the death message is what tells them apart: the bad ending shows "Fall N times" and never "stand up".

## The year

**The loop moves time forward.** Kaamos wrote the note, the player is Kaamos, and time moves on each time he dies, even though the world resets to the morning. Kaamos has been stuck in the loop for over six thousand days. From 1932-09-10, the date on the journal cover, 6,000 days reach 1949-02-13 and 6,322 reach 1950-01-01. The story therefore lands in the nuclear era on its own, and the 1932 journal date stays as it is.

How the game could show it:

- A calendar item in the cabin keeps ticking forward (idea).
- The "Day N" marker (*"You wake up. Day N begins..."*, in `mod-src/winterheart_time/src/main/java/io/github/soldierdonkey2/winterheart_time/TimeSystem.java`) is reworded to count nights slept or days perceived, not calendar days.
- Confusion over the flow of time could become a real feature (idea).
- The bad ending's *"WAKE UP, WAKE UP"* plays on it (see [Bad ending](#bad-ending-insanity)).

In game: only the 1932 date on the journal cover. Nothing yet moves a calendar date or shows that time has passed. This is much more complex to build than simply setting the story in a later year.

## Endings

Every ending is a loss. The player can choose how to lose, but not whether.

### Beacon ending: the catastrophe

1. Kaamos lights the beacon (as the captain's log begged).
2. Something horrible happens that ruins the whole continent. Current idea: the beacon is part of an early warning system, and lighting it raises a false alarm that launches a series of nuclear missiles. The loop has carried time into the nuclear era (see [The year](#the-year)), but missile early warning dates from the late 1950s, later than six thousand days reaches (open question 1).
3. The player dies, whatever the catastrophe is.
4. The player wakes in **the afterlife cabin**:
   - looks like the starting cabin, but fully repaired, with no carcasses outside,
   - the player is in adventure mode and always at full health,
   - the landscape around it is completely white, as if from a dream,
   - it is a separate dimension, not part of the overworld.

   This shows the afterlife looping straight back to the cabin.
5. **This is the end of the game.** The player can explore the white landscape, but it loops: walking away always brings them smoothly back to the cabin, as if the world were a small sphere. The render distance is low enough that the player never sees the seam. Inside the cabin are a book of credits and some notes about the game.

The captain knew what the beacon does: he wanted to trigger the early warning system to put his crew, who were turning into wendigos, out of their misery (see [Characters](#characters)).

In game: only lighting the beacon. Right-clicking a beacon builds its iron base ([beacon-base](../features/beacon-base.md)). Nothing else is built yet.

### Bad ending: insanity

1. **Trigger:** low sanity **and** day 50 (the eternal night). Before day 50, a low-sanity death is still a free respawn. The eternal night's sanity drain pushes every player towards this unless they light the beacon.
2. Kaamos can't take it any more. The seppuku animation plays, and the screen gets bloodier by the second.
3. Text like **"WAKE UP, WAKE UP"** flashes on screen. There is no new morning to wake up to: on day 50 the night never ends.
4. An ominous death screen shows **"Fall N times"**, with no "stand up N+1".
5. This is **permadeath**: the player can't wake up again. The death screen stays, with only two buttons, **Spectate** and **Title screen**, like the hardcore death screen.
6. Kaamos becomes the wendigo (see below).
7. **Spectate** (tentative) opens a humorous wendigo simulator: the player is the wendigo and hunts a dummy-player Kaamos. Each kill shows how long it took to eat Kaamos and teleports the player to a new random spawn point.

In game: the seppuku animation plays once at sanity ≤ 10 on any day ([seppuku](../features/seppuku.md)), but it doesn't kill the player. The day-50 condition, the blood overlay, the "WAKE UP" text, the death screen and permadeath are planned.

### The wendigo

The wendigo is what Kaamos becomes in the bad ending. It closes the circle: the beast hunting the herds and Kaamos is a Kaamos from an earlier loop who broke. The smell under the floor, the vanishing herds and the starvation all point to this.

Kaamos is not the only one: the ferry crew became wendigos too, while their captain, who never ate human flesh, did not.

How the game shows it is undecided. Ideas so far:

- give the wendigo a name close to "Kaamos",
- have the wendigo sometimes disguise itself as the player.

## Motifs

- **The polar night**: Kaamos's name, the shortening days, the eternal night from day 50.
- **"Fall seven times, stand up eight"** (Japanese proverb *nana korobi ya oki*): the death message. Taking away the "stand up" half marks the end of the loop. The seppuku in the bad ending belongs to the same Japanese motif.
- **Hunger and iron**: the wendigo smells of iron (blood). The gore titles in `config/paxi/datapacks/winterheart_survival/data/winterheart/proximity/rules.json` will all be rewritten; the scrambled `You must ######## it.` doesn't mean anything yet.
- **Suicide as the end of the watch**: Connors hangs himself; Kaamos's bad ending is seppuku.
- **White**: the snow outside, and the pure white afterlife.

## Open questions

1. **How passing time works.** See [The year](#the-year).
   - How far forward does each death move time, and does a night slept move it too?
   - What is the present date? Six thousand days only reach 1949, while missile early warning (late 1950s) needs about 9,000 days or more. Does the loop run longer, or does the beacon's catastrophe change?
   - Does the calendar item show a date or a count?
   - Is "the flow of time is confusing" a feature the player is meant to notice?
   - The world resets but time moves on: what has changed beyond the loop (the capitol, the beacon's early warning system)?
2. **The early journey south.** Does the opening become a journey south to the watchtower, where Kaamos finds Connors dead? If so, does the World Painter path (`tools/worldpainter/`, its "Escape path" and the unused `escape_camp` structure) get renamed to match?
3. **The introduction note's wording.** It is from an earlier-loop Kaamos. Does *"Find me down south"* stay (pointing to the watchtower), or change?
4. **How low is "low sanity"** for the bad ending? The seppuku threshold now is 10 (of 100). Does starvation play a part ("go insane and starve")?
5. **How is the catastrophe shown?** A cutscene, sky effects, explosions over the landscape, or only the death and a message?
6. **How is Kaamos tied to the wendigo?** Name, disguise as the player, a skin, behaviour, a journal entry? Does the wendigo met in a normal run imply an earlier Kaamos already broke?
7. **Colonel Connors.** What did he tell Kaamos about the monsters, and did he know about the beacon? Was he in the loop too?
8. **The captain.** Why was he unable to trigger the early warning system himself?
9. **The smell under the cabin floor.** Whose remains are they?
