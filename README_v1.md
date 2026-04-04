# BlissDuels - Version 1 Notes

This document describes the scope of the initial `v1.0` release.

## v1.0 Scope

- Core gem system with generated gem items
- Pristine-only energy model (`5`)
- Tier 1 and Tier 2 gem support
- Gem auto-grant for gemless players (configurable)
- `/gem` command with config reload support
- Gem selector items (Tier 1 and Tier 2)
- Flux kinetic charging with cap behavior
- Trust manager and trust command flow

## Included Gem Families

- Fire
- Flux
- Strength
- Life
- Speed
- Puff
- Wealth
- Astra

## Commands Added/Available in v1.0

- Player/admin gem access: `/gem`, `/getgem`
- Flux charge tools: `/watts`, `/hiddenwatts`, `/startcharging`, `/stopcharging`, `/chargemenu`
- Flux admin tools: `/maxwatts`, `/resetwatts`, `/reset-charge`
- Utility/admin: `/trust`, `/dbg`, `/giveobsidian`

## Compatibility

- API version: `1.21.11`
- Java target: `21`
- Gradle project version: `1.0`

## Notes

- Plugin metadata is defined in `src/main/resources/plugin.yml`.
- Build version is defined in `build.gradle`.
- This file is intended as a stable baseline for future version changelogs.

