# Reading Streak Feature Documentation

## Overview
The goal pill now displays real-time data about user progress and reading streaks.

## Features Implemented

### 1. **Total Topic Count**
- Displays: "X of Y topics done"
- Counts all topics across all 3 chapters (24 + 13 + 1 = 38 total topics)
- Updates dynamically as user completes topics

### 2. **Reading Streak**
- Displays: "🔥 X-day streak" or "Start your streak!"
- Calculates consecutive days of reading activity
- A streak is maintained if the user completes at least one topic per day

## How Streak Calculation Works

### Streak Rules:
1. **Active Streak**: User must complete at least one topic today or yesterday
2. **Consecutive Days**: Counts backward from today/yesterday for consecutive days with activity
3. **Broken Streak**: If there's a gap of more than 1 day, the streak resets to 0
4. **Grace Period**: Allows for late-night reading (counts yesterday as valid)

### Example Scenarios:

**Scenario 1: Active Streak**
- Today: Completed 2 topics ✅
- Yesterday: Completed 1 topic ✅
- 2 days ago: Completed 3 topics ✅
- 3 days ago: No activity ❌
- **Result**: 3-day streak

**Scenario 2: Broken Streak**
- Today: Completed 1 topic ✅
- Yesterday: No activity ❌
- 2 days ago: No activity ❌
- 3 days ago: Completed 2 topics ✅
- **Result**: 0-day streak (broken)

**Scenario 3: New User**
- No completed topics yet
- **Result**: "Start your streak!" message

## Database Changes

### New Queries Added to AppDao:

```java
// Get total topic count across all chapters
@Query("SELECT COUNT(*) FROM topics")
int getTotalTopicCount();

// Get completed topic count across all chapters
@Query("SELECT COUNT(*) FROM topics WHERE completed = 1")
int getTotalCompletedCount();

// Get distinct dates when topics were completed (for streak calculation)
@Query("SELECT DISTINCT date(lastViewed / 1000, 'unixepoch', 'localtime') as date 
       FROM topics 
       WHERE completed = 1 AND lastViewed > 0 
       ORDER BY date DESC")
List<String> getCompletedDates();
```

## Code Implementation

### HomeFragment.java Updates:

1. **updateGoalPill()** - Fetches and displays current stats
   - Runs in background thread (io executor)
   - Updates UI on main thread
   - Called on resume and after loading chapters

2. **calculateStreak()** - Calculates current reading streak
   - Parses completion dates from database
   - Checks for consecutive days
   - Returns streak count (0 if broken)

## UI Updates

The goal pill updates automatically when:
- ✅ App is opened (onResume)
- ✅ Chapters are loaded
- ✅ User completes a topic (via existing completion tracking)

## Performance Considerations

- All database queries run on background thread
- UI updates happen on main thread
- Efficient date parsing using SimpleDateFormat
- Minimal memory footprint (only stores date strings)

## Future Enhancements (Optional)

1. **Streak Milestones**: Show badges for 7-day, 30-day, 100-day streaks
2. **Streak Notifications**: Remind users to maintain their streak
3. **Streak History**: Show longest streak achieved
4. **Daily Goal**: Allow users to set custom daily reading goals
5. **Streak Recovery**: Allow one "freeze" day per week to maintain streak

## Testing Recommendations

1. Test with no completed topics (should show "Start your streak!")
2. Test with topics completed today (should show current streak)
3. Test with gap in completion dates (should reset to 0)
4. Test across midnight boundary (grace period)
5. Test with all topics completed (should show 38/38)
