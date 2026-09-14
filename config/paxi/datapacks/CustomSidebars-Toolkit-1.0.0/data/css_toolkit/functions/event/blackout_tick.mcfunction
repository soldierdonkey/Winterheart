execute if score timer css_toolkit matches 1.. run scoreboard players remove timer css_toolkit 1
execute if score timer css_toolkit matches 1.. run schedule function css_toolkit:event/blackout_tick 1s replace
execute if score timer css_toolkit matches 120 run tellraw @a {"text":"[Custom Sidebars Toolkit] Two minutes remain in the event demo.","color":"yellow"}
execute if score timer css_toolkit matches 30 run tellraw @a {"text":"[Custom Sidebars Toolkit] Critical timer threshold reached.","color":"red"}
execute if score timer css_toolkit matches 0 run tellraw @a {"text":"[Custom Sidebars Toolkit] Event demo timer expired.","color":"dark_red","bold":true}
