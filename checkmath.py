#!/usr/bin/env python3

event = ''
remains = 0

for time in range(0, 23999, 100):
    night = False

    if time >= 0 and time < 12000:
        event = 'day'
        remains = (12000 - time)/12000
    elif time >= 12000 and time < 13000:
        event = 'sunset'
        remains = (13000-time) / 1000
    elif time >= 13000 and time < 23000:
        event = 'night'
        remains = (23000-time) / 10000
        night = True
    elif time >= 23000:
        event = 'sunrise'
        remains = (24000-time) / 1000

    if event is not None:
        print(time, event, '{}%'.format(int(remains*100)), night)
