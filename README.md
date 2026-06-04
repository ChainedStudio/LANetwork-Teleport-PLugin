# ObscureTeleport

A robust, highly customizable proprietary teleportation management system built for modern Minecraft servers. This plugin delivers essential player-to-player utility commands alongside dynamic visual and audio feedback, giving server administrators granular control over gameplay pacing and aesthetic styling.

---

## 🚀 Features

* **Advanced TPA System:** Includes robust player-to-player teleportation requests (`/tpa`, `/tpahere`) featuring customizable request timeouts, toggleable availability, automatic acceptance toggles (`/tpaauto`), and request ignoring.
* **Mass Requests:** Built-in administrative capability to send a `/tpahere` request to every active online player simultaneously (`/tpahereall`).
* **Visual Title Countdowns:** Displays an immersive title and subtitle countdown directly on the player's screen when preparing to teleport, enhancing the user experience.
* **Dynamic Audio Engine:** Triggers unique sound-based cues—such as a creeper hiss or note block chime for teleportation milestones—with fully configurable sound types, volume, and pitch customizable directly via config.
* **Movement Detection Warm-up:** Implements a movement-sensitive delay before teleportation occurs. Moving from the initial block during the countdown cancels the process to prevent unfair escapes.
* **Random Teleportation (RTP):** Provides a comprehensive random teleport system allowing players to find new areas safely within configured boundaries while avoiding dangerous terrain like oceans or lava lakes.
* **Interactive Dynamic GUIs:** Built-in graphical dashboards for player destination selection and administrative configuration management.
* **Rich Text Customization:** Built-in native support for Kyori Adventure MiniMessage, hex codes (`&#FFFFFF`), and legacy color formats (`&c`), allowing for heavily stylized chat elements, hover text tooltips, and clickable text buttons.

---

## 🛠 Commands & Permissions

| Command | Description | Default Permission |
| :--- | :--- | :--- |
| `/tpa <player>` | Sends a request to teleport to another player. | *Available by default* |
| `/tpahere <player>` | Requests that another player teleport to your current location. | *Available by default* |
| `/tpahereall` | Sends a `/tpahere` request to every online player. | `obscure.tpahereall` |
| `/tpaccept` | Accepts a pending teleportation request. | *Available by default* |
| `/tpadeny` | Declines a pending teleportation request. | *Available by default* |
| `/tpatoggle` | Toggles whether incoming teleport requests are allowed or ignored. | *Available by default* |
| `/tpaauto` | Toggles automatic acceptance of incoming TPA requests. | *Available by default* |
| `/tpaignore <player>` | Blocks a specific player from sending you requests. | *Available by default* |
| `/tpaunignore <player>` | Unblocks a previously ignored player. | *Available by default* |
| `/rtp` | Teleports the player to a random location within safe boundaries. | *Available by default* |
| `/rtpmenu` | Opens the graphical GUI dashboard selection panel for RTP worlds. | *Available by default* |
| `/teleportconfig [reload]` | Opens the live setup admin GUI dashboard or reloads configuration files. | `obscure.admin` |
| `/debug` | Toggles administrative universal solo-testing parameter overrides. | `obscure.admin` |

---

## ⚙️ Configuration Files

The plugin utilizes a dual-file setup for streamlined system management. Updates to messages and sounds can be tweaked seamlessly on the fly using `/teleportconfig reload`.

### `config.yml`
Handles internal logic configurations, including countdown delays, safe-zone attempt thresholds, and menu icon layout slot grids.

```yaml
# Core settings for the teleportation engine
tpa:
  timeout: 60
  countdown-duration: 3 # Sets warm-up duration before warp execution (In Seconds)

rtp:
  min-radius: 1000
  max-radius: 5000
  max-attempts: 15
  allowed-worlds:
    - "world"
    - "world_nether"

sounds:
  send-request: "ENTITY_EXPERIENCE_ORB_PICKUP"
  receive-request: "BLOCK_NOTE_BLOCK_CHIME"
  teleport: "ENTITY_ENDERMAN_TELEPORT"
  deny: "BLOCK_ANVIL_LAND"

gui:
  title: "§6§lRTP Destination Selection"
  background-item: "minecraft:gray_stained_glass_pane"
  items:
    overworld_button:
      material: "minecraft:grass_block"
      slot: 11
      name: "§a§lOverworld Dimension"
      lore:
        - "§7Click to execute a random teleport"
        - "§7within the native overworld surface area."
      action-type: "WORLD"
      action-target: "world"
