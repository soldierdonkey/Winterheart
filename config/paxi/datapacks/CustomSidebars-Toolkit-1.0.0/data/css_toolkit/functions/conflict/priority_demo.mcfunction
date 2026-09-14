function css_toolkit:clear
function css_toolkit:setup

cssidebar add css_toolkit:conflict_server {"text":"Server Layer","bold":true}
cssidebar set css_toolkit:conflict_server theme military_modern
cssidebar set css_toolkit:conflict_server layer server
cssidebar set css_toolkit:conflict_server priority 10
cssidebar set css_toolkit:conflict_server global true
cssidebar line add css_toolkit:conflict_server text normal {"text":"This is a persistent server panel."}

cssidebar add css_toolkit:conflict_mission {"text":"Mission Layer","bold":true}
cssidebar set css_toolkit:conflict_mission theme rpg_souls
cssidebar set css_toolkit:conflict_mission layer mission
cssidebar set css_toolkit:conflict_mission priority 50
cssidebar set css_toolkit:conflict_mission global true
cssidebar line add css_toolkit:conflict_mission text normal {"text":"This mission beats the server panel."}

cssidebar add css_toolkit:conflict_event {"text":"Event Layer Wins","bold":true}
cssidebar set css_toolkit:conflict_event theme emergency_red
cssidebar set css_toolkit:conflict_event layer event
cssidebar set css_toolkit:conflict_event priority 100
cssidebar set css_toolkit:conflict_event exclusive true
cssidebar set css_toolkit:conflict_event global true
cssidebar line add css_toolkit:conflict_event text section {"text":"CONFLICT TEST"}
cssidebar line add css_toolkit:conflict_event text critical {"text":"Exclusive event panel is active."}
cssidebar line add css_toolkit:conflict_event text muted {"text":"Run /cssidebar debug for resolver details."}

tellraw @a {"text":"[Custom Sidebars Toolkit] Conflict demo active. The event panel should win.","color":"red"}
