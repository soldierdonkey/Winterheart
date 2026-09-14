# Copyable Examples

## Event Timer

```mcfunction
scoreboard objectives add event_data dummy
scoreboard players set timer event_data 300
scoreboard players set progress event_data 60
scoreboard players set max event_data 100

cssidebar add mypack:event {"text":"OPERATION ACTIVE","bold":true}
cssidebar set mypack:event theme emergency_red
cssidebar set mypack:event layer event
cssidebar set mypack:event priority 100
cssidebar set mypack:event exclusive true
cssidebar set mypack:event global true
cssidebar line add mypack:event text section {"text":"OBJECTIVE"}
cssidebar line add mypack:event text accent {"text":"Hold the bridge"}
cssidebar line add mypack:event timer warning {"text":"Time left"} timer event_data seconds
cssidebar line add mypack:event progress normal {"text":"Control"} progress event_data max event_data
```

## Mission Panel

```mcfunction
scoreboard objectives add mission_data dummy
scoreboard players set keys mission_data 2
scoreboard players set keys_max mission_data 4

cssidebar add mypack:mission {"text":"Current Mission","bold":true}
cssidebar set mypack:mission theme rpg_souls
cssidebar set mypack:mission layer mission
cssidebar set mypack:mission priority 50
cssidebar set mypack:mission global true
cssidebar line add mypack:mission text section {"text":"OBJECTIVES"}
cssidebar line add mypack:mission text normal {"text":"Find the generator"}
cssidebar line add mypack:mission progress normal {"text":"Keys"} keys mission_data keys_max mission_data
```

## Server Status

```mcfunction
scoreboard objectives add server_data dummy
scoreboard players set online server_data 42

cssidebar add mypack:server {"text":"Server","bold":true}
cssidebar set mypack:server theme military_modern
cssidebar set mypack:server layer server
cssidebar set mypack:server priority 10
cssidebar set mypack:server global true
cssidebar line add mypack:server score accent {"text":"Online"} online server_data
cssidebar line add mypack:server text muted {"text":"Zone: Safe base"}
```
