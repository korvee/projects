import csv
from matplotlib import pyplot as plt
from datetime import datetime

filename = "sitka_weather_07-2021_simple.csv"
with open(filename) as f:
    reader = csv.reader(f)
    header_row = next(reader)
    
    dates,highs = [],[]
    for row in reader:
        curr_date = datetime.strptime(row[2],"%Y-%m-%d")
        dates.append(curr_date)
        high= int(row[4])
        highs.append(high)

    fig = plt.figure(dpi=128, figsize=(10, 6))
    plt.plot(dates,highs, c='red')
    plt.title("Daily high temperatures, July 2014", fontsize=24)
    plt.xlabel('', fontsize=16)
    fig.autofmt_xdate()
    plt.ylabel("Temperature (F)", fontsize=16)
    plt.tick_params(axis='both', which='major', labelsize=16)
    plt.show()