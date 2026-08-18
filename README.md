# Geopolitica

Geopolitica is a plugin for Minecraft servers that lets players build towns, states, and nations. Players can make **towns**. These **towns** can join together to make **states**. These **states** can then join together to make **nations**. Each **town** **state** and **nation** has its bank and leader.

## Political Hierarchy

```

Nation

├─ States

│   └─ Towns

└─ Towns

```

- A **town** is the unit. It has residents, leaders, and a bank. Each **town** can claim land. Set its own rules.

- A **state** is a group of **towns** that work together. A **state** has its leader and bank. The leader of a **state** does not have to be the leader of the *town** that is the capital.

- A **nation** is a group of **states**. **Towns** that work together. A **nation** has its leader and bank. The leader of a **nation** is always the leader of the *town** that is the capital.

## Features

- Players can make their **towns** and claim land.

- Players can. Leave **towns** and **nations**.

- **Towns** and **nations** can have their banks and rules.

- Players can use a menu to do things like join or leave **towns** and **nations**.

- The plugin uses a database to store all the information about **towns** **states** and **nations**.

- Other plugins can use the Geopolitica plugin to get information about **towns** **states** and nations*.

## Requirements

- You need to have Java 21 installed.

- You need to have a Minecraft server that uses the Paper or Spigot plugin.

- You can also use the Vault plugin to add features to Geopolitica.

## Installation

1. Download the Geopolitica plugin.

2. Put the plugin in your Minecraft servers plugins folder.

3. Start your server. Wait for the plugin to make a configuration file.

4. Edit the configuration file to change the settings.

## Building

You can build Geopolitica yourself using Java 21 and Maven .

## Configuration

You can change the settings for Geopolitica in the configuration file.

| Setting. Default | Description |

|---|---|---|

| storage type | sqlite | The type of database used (MySQL to be added) |

Town min name length | 3 | The minimum length of a town's name.

| Town max name length | 32 | The maximum length of a town's name. |

| Town max claims | 64 | The maximum number of claims a town can have. |

Town free claims | 4 | The number of free claims a town gets. |

Town cost per claim | 500.0 | The cost of each claim after the claims.

| Town refund per claim | 250.0 | The refund, for each claim that's unclaimed. |

## Commands

### `/town`

You can use the `/town` command to do things like make a town join a town or leave a town.

### `/Nation`

You can use the `/nation` command to do things like make a nation join a nation or leave a nation.

#### `/Nation state`

You can use the `/nation state` command to do things like make a state, join a state, or leave a state.

### National State Commands

You can use these commands to manage your nation-state.

/Nation state leader <player>. This command is used to reassign the state's leader to the player you choose.

/nation state secede <newNationName>. Use this command to split off into a brand-independent nation.

/nation state capital <town>. This command relocates the capital to another member town that you specify.

/nation state kick <town>. You can expel a member town from your nation-state using this command.

/nation state tax <town> <amount>. Collect tax from a member town's bank by using this command and specifying the amount you want to collect.

/nation state withdraw <amount>. You can withdraw an amount from the nation-state.

/nation-state description <text>. Use this command to add a description to your nation-state.

/nation state color <#hex>. You can change the color of your nation-state using this command and specifying the hex code.

/nation state open. This command toggles your nation-state between invite-only and

Any state member can use the following command:

/nation state deposit <amount>. Members can deposit an amount into the nation-state.

### Admin Commands

If you have the geopolitics. admin permission, you can use the following commands:

/gadmin reload. This command reloads the configuration outlined in Config.yml.

/gadmin freeze|unfreeze <town>. You can unfreeze a town using this command.

/gadmin delete <town>. Use this command to delete a town.

/gadmin setbank <town> <amount>. You can set the bank amount for a town using this command.

/gadmin nation freeze/unfreeze/delete/setbank <nation> [amount]. These commands allow you to manage nations.

/gadmin state freeze/unfreeze/delete/setbank <state> [amount]. You can also use these commands to manage states.

## Permissions

Here are the permissions you can have:

| Permission Default | Description |

|---|---|---|

| geopolitica.admin | op | This permission gives you administrative access and allows you to use the /gadmin command to bypass claim protection and leader checks. |

| Geopolitica.town.create true | This permission allows you to found a town. |

| Geopolitica.town.claim true | You need this permission to claim chunks for your town. |

| Geopolitica.town.bypass | op | This permission allows you to bypass claim protection. |

| Geopolitica.nation.create true | You need this permission to found a nation. |

Founding a state does not have its permission node; it is gated by the MANAGE_NATION town rank permission on the founding players' town, the same as joining or leaving a nation or state.

## Storage

The data is stored in an SQLite database called geopolitica.db, which is located in the plugins/Geopolitica/ directory.

The database has tables for towns, ranks and permissions, residents, claims and claim permissions, nations, states, and nation relations.

All writes happen asynchronously off the server thread.

Schema migrations, which are columns, on existing tables are applied automatically when you start up the server.

## Developer API

To use the developer API, you need to add geopolitica-api as a provided or compileOnly dependency.

Then you can use the following code to get the NationService:

```java

NationService nations = GeopoliticaAPI.getNationService();

```

Or you can use the Bukkit services manager:

```java

NationService nations = Bukkit.getServicesManager().load(NationService.class);

```

The available services are TownService, ClaimService, and NationService.

The NationService also covers states and inter-nation diplomacy.

There are also events that you can hook into:

TownCreateEvent, TownDisbandEvent, ResidentJoinTownEvent, ResidentLeaveTownEvent, ClaimCreateEvent, ClaimRemoveEvent,

NationCreateEvent, NationDisbandEvent, NationRelationChangeEvent, TownNationChangeEvent, StateCreateEvent, StateDisbandEvent,

StateSecedeEvent, TownStateChangeEvent.

## Known Limitations and Roadmap

There are a few things that are not yet implemented:

MySQL storage is config-ready but not implemented, so SQLite is always used.

Upkeep is. Not yet charged by a scheduled task.

There is no localization system yet, even though there is a general one. language config key.

API is planned to have info-grabbing features for comprehensive abilities. 

