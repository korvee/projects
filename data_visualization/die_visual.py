from die import Die
import pygal


die = Die()
results = []
for roll_num in range(1000):
    result = die.roll()
    results.append(result)

frequencies = []
for value in range(1,die.num_sides+1):
    frequency = results.count(value)#判断列表里有多少个值为value的元素
    frequencies.append(frequency)

hist = pygal.Bar()

hist._title = "Results of rolling one D6 1000 times."
hist.x_labels = ["1","2","3","4","5","6"]
hist._x_title = "Result"
hist._y_title = "Frequency of Result"

hist.add("D6",frequencies)
hist.render_to_file("die_visual.svg")

