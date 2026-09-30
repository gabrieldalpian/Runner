# Runner

I built this program because I wanted a simple way to find new Ironman races near me and get alerted as soon as they show up. It checks race listings online, filters for Ironman-style events near my area, and sends a Discord message whenever a new event appears that I haven't seen before.

## Why this exists

I wanted something lightweight and automatic for tracking races that matter to me, especially Ironman events. Instead of checking race websites manually every day, this app does the work for me and sends an alert straight to Discord.

## What it does

- looks up upcoming endurance races from RunSignup
- focuses on triathlon and Ironman-style events
- filters by location and radius around my ZIP code
- tracks previously seen race IDs so I only get alerts for new ones
- prints the race details in the terminal
- posts new events to a Discord channel

## Main settings

At the top of `RaceAlerts.java`, you can tune the search:

```java
static String ZIPCODE = "10001";
static int RADIUS_MILES = 15;
static String RACE_TYPE = "triathlon";
static int HOURS_BETWEEN_CHECKS = 6;
static String SEEN_FILE = "seen-races.txt";
```

For Ironman alerts, you would usually keep `RACE_TYPE` as `triathlon` and use a ZIP code near your area, or adjust the filter to look for Ironman-specific names and distances.

## Example use case

If I live near a metro area, this program can keep checking for:

- Ironman events
- 70.3 races
- nearby triathlons in the region
- newly posted races that were not previously seen

When a new race appears, it posts an alert like:

- race name
- date
- city
- distances
- registration status
- link

## Discord workflow

This is designed to be a simple personal alert system. I send the output to a Discord webhook, so every time a new Ironman-style event appears near my area, I get a notification right away without checking websites manually.

## Data tracking

The program saves seen race IDs in `seen-races.txt` so it does not spam the same race over and over again.