<!--
CurseForge project page text.
Summary field (short description, max ~250 chars):
Something tall stands in the distance and stares. Walk closer and it runs. Hide and it sinks into the ground... In the mines it digs through the rock to reach you. Don't hide. Just run.

Suggested categories: Mobs, Adventure and RPG
Description below: paste into the CurseForge description editor in Markdown mode.
-->

<p align="center"><img src="https://raw.githubusercontent.com/vcbgf5/it-was-tall/main/tools/icon/icon_1024.png" width="300" alt="It Was Tall"></p>

# It Was Tall

> *It stands in the distance. It watches. Don't let it follow you home.*

**It Was Tall** is a horror mod for **Fabric 1.21.1**. A thin figure, more than three blocks tall, with arms that hang past its knees and a pale, screaming face, starts showing up in your world. At first it only watches. Then it comes closer.

---

## 🌑 The encounter

- At night it appears **far away, in front of you**, and just **stares**.
- Walk towards it, or keep looking at it for too long, and it **screams and runs at you**.
- Make it to **shelter** (a roof over your head and walls around you) and it stops... raises its arms... and **sinks into the ground**.
- A moment later you see **its face for a tenth of a second**.
- Then the screen tells you: **`don't hide. just run.`**

## ⛏️ In the mines

Spend enough time underground and the mine stops being safe:

- **It digs its way to you.** It appears inside the rock up to 30 blocks above you and **tunnels down with an iron pickaxe**. You can see the blocks cracking and hear it getting closer. It cannot break obsidian or bedrock.
- **Digging sounds** come closer from above... then stop.
- **Your torches go out**, one by one, from the farthest to the closest. Only the ones you are not looking at.
- **It stands right behind you.** You hear breathing. Don't turn around.

If it is chasing you and your tunnel is too low for it, it just starts digging again.

## 🔊 Sounds that follow you

- **82 custom sounds**: screams with a breaking voice, growling, sniffing, a muffled heartbeat, whispers and breathing behind your back, drones, wind, creaks, distant screams, chains, falling rocks, dripping water, a reversed voice, and ringing in your ears after a jumpscare.
- **Small scares every 20–60 seconds**: a twig snapping behind you, a pebble falling from the ceiling, a tap on your window, a door creaking, limping footsteps right behind you, a whisper of *"run..."* in your ear, a giggle, a music box...
- **Ambience depends on where you are**: mines, outside at night, inside your base at night, daytime.
- When it is close, you hear your **heartbeat**.
- **Red, flickering, glitching messages** sometimes appear on your screen.

## 👥 Multiplayer

- **Every player is haunted separately**, with their own timers for appearances, scares, messages and mine events.
- **Only you hear your scares** and only you see your messages and jumpscares. Your friends just see a very tall thing standing in the field.
- It watches one player, but **whoever walks up to it, or hits it, becomes its target**.
- On its own, it never shows up for a player who already has one nearby, and there is a server-wide limit (3 by default).
- Admins can scare specific players with commands (see below), also from the server console.

## ⌨️ Commands (OP)

| Command | What it does |
|---|---|
| `/itwastall spawn [distance] [player]` | It appears in front of the player and stares |
| `/itwastall haunt [players]` | It appears the natural way, in the distance |
| `/itwastall jumpscare [players]` | 0.1 s jumpscare |
| `/itwastall scare [players]` | a small scare sound |
| `/itwastall message [players] [text]` | red on-screen message (default: *don't hide. just run.*) |
| `/itwastall mine dig\|sounds\|torches\|peek [player]` | force a mine event |
| `/itwastall status` | how many of them exist on the server |

Example: `/itwastall message Steve it sees you.`

## ⚙️ Config

`config/itwastall.json` lets you change almost everything, for example:

- `jumpscares`, `jumpscareMillis`: turn the jumpscare image off (sound only) or change its length
- `hauntEvents`, `hauntMinMinutes`, `hauntMaxMinutes`, `hauntOnlyAtNight`, `maxActiveIts`
- `mineEvents`, `digging`, `digSlowdown`, `mineMinSecondsUnderground`
- `scareSounds`, `scareMinSeconds`, `scareMaxSeconds`, `ambientSounds`
- `scareMessages`, `scareText`, `messages`: write your own messages

Digging also respects the `mobGriefing` gamerule.

## 📦 Requirements

- **Minecraft 1.21.1**
- **Fabric Loader 0.20.1** or newer
- **Fabric API**
- **GeckoLib** (Fabric, 1.21.1) **4.9.3** or newer

In multiplayer, install the mod **on the server and on every client**.

## ⚠️ Warning

This mod contains **jumpscares, a sudden flashing image and loud sounds**. If you are sensitive to flashing images, set `"jumpscares": false` in the config.

## 🛠️ Planned

- Fear stages that slowly escalate the haunting
- It learns the layout of your base: knocking on your windows, opening doors, putting out your lights
- Tasks like *"Don't go outside"* and *"Hide and seek"*
- Creepy structures

---

*Source code: [github.com/vcbgf5/it-was-tall](https://github.com/vcbgf5/it-was-tall)*
