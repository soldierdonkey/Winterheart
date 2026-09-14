function css_toolkit:clear
function css_toolkit:setup
scoreboard players set keys css_toolkit 2
scoreboard players set keys_max css_toolkit 4

cssidebar add css_toolkit:mission {"text":"Current Mission","bold":true}
cssidebar set css_toolkit:mission theme rpg_souls
cssidebar set css_toolkit:mission layer mission
cssidebar set css_toolkit:mission priority 50
cssidebar set css_toolkit:mission global true
cssidebar set css_toolkit:mission hide_vanilla true
cssidebar set css_toolkit:mission anchor top_right
cssidebar set css_toolkit:mission width 184
cssidebar line add css_toolkit:mission text section {"text":"OBJECTIVES"}
cssidebar line add css_toolkit:mission text normal {"text":"Find the old generator"}
cssidebar line add css_toolkit:mission text muted {"text":"Area: Lower Sector B"}
cssidebar line add css_toolkit:mission spacer
cssidebar line add css_toolkit:mission score accent {"text":"Keys found"} keys css_toolkit
cssidebar line add css_toolkit:mission progress normal {"text":"Key progress"} keys css_toolkit keys_max css_toolkit

tellraw @a {"text":"[Custom Sidebars Toolkit] Mission demo active.","color":"gold"}
