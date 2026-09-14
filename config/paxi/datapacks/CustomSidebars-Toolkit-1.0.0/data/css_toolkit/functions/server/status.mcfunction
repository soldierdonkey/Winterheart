function css_toolkit:clear
function css_toolkit:setup
scoreboard players set online css_toolkit 42
scoreboard players set credits css_toolkit 1200

cssidebar add css_toolkit:server_status {"text":"Server Status","bold":true}
cssidebar set css_toolkit:server_status theme military_modern
cssidebar set css_toolkit:server_status layer server
cssidebar set css_toolkit:server_status priority 10
cssidebar set css_toolkit:server_status global true
cssidebar set css_toolkit:server_status hide_vanilla true
cssidebar set css_toolkit:server_status anchor top_right
cssidebar set css_toolkit:server_status width 172
cssidebar line add css_toolkit:server_status text section {"text":"PLAYER INFO"}
cssidebar line add css_toolkit:server_status score accent {"text":"Online"} online css_toolkit
cssidebar line add css_toolkit:server_status text normal {"text":"Rank: Soldier"}
cssidebar line add css_toolkit:server_status score warning {"text":"Credits"} credits css_toolkit
cssidebar line add css_toolkit:server_status text muted {"text":"Zone: Safe base"}

tellraw @a {"text":"[Custom Sidebars Toolkit] Server status demo active.","color":"green"}
