function css_toolkit:clear
function css_toolkit:setup
scoreboard players set timer css_toolkit 300
scoreboard players set max_time css_toolkit 300
scoreboard players set signal css_toolkit 72
scoreboard players set signal_max css_toolkit 100
scoreboard players set relays css_toolkit 2

cssidebar add css_toolkit:blackout {"text":"OPERATION: BLACKOUT","bold":true}
cssidebar set css_toolkit:blackout theme emergency_red
cssidebar set css_toolkit:blackout layer event
cssidebar set css_toolkit:blackout priority 100
cssidebar set css_toolkit:blackout exclusive true
cssidebar set css_toolkit:blackout global true
cssidebar set css_toolkit:blackout hide_vanilla true
cssidebar set css_toolkit:blackout anchor top_right
cssidebar set css_toolkit:blackout width 196
cssidebar line add css_toolkit:blackout text section {"text":"PRIMARY OBJECTIVE"}
cssidebar line add css_toolkit:blackout text accent {"text":"Defend the relay tower"}
cssidebar line add css_toolkit:blackout spacer
cssidebar line add css_toolkit:blackout timer warning {"text":"Extraction window"} timer css_toolkit seconds
cssidebar line add css_toolkit:blackout text critical {"text":"Status: Zone contested"}
cssidebar line add css_toolkit:blackout score accent {"text":"Relays online"} relays css_toolkit
cssidebar line add css_toolkit:blackout progress normal {"text":"Signal integrity"} signal css_toolkit signal_max css_toolkit

schedule function css_toolkit:event/blackout_tick 1s replace
tellraw @a {"text":"[Custom Sidebars Toolkit] Event demo started: Operation Blackout.","color":"red"}
