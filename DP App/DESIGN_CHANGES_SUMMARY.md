# Design Changes Summary

## Overview
Updated the chapter and topic item designs to match the new UI specifications with improved visual hierarchy and status indicators.

## Changes Made

### 1. Home Fragment Chapter Items (`item_chapter.xml`)
- **Changed icon from emoji TextView to ImageView**
  - Replaced emoji text with `ImageView` using vector drawable
  - Created `ic_chapter_default.xml` vector drawable for chapter icon
  - Icon now displays as a proper image instead of emoji character
  - Maintains the same rounded background container with amber color

### 2. Chapter List Topic Items (New `item_topic.xml`)
- **Created separate layout for topic list items**
  - New layout file: `item_topic.xml`
  - Displays topic number in a rounded square container
  - Shows topic title, description, time estimate, and status
  - Supports three states: New, Reading (in progress), and Done

- **Topic Number Display**
  - Rounded square container showing the topic number
  - White background for new/in-progress topics
  - Green background (`badge_done_bg`) for completed topics
  - Number text color changes based on completion status

- **Status Indicators**
  - "New" badge for unstarted topics
  - "Reading" badge for in-progress topics (yellow background)
  - "Done" badge for completed topics (green background)
  - Time estimate shown with clock icon (⏱)

### 3. Stroke Colors for Progress States
- **Added new color resource**
  - `stroke_in_progress`: `#F5A623` (amber/yellow) for topics being read
  - Default stroke: `#D3D1C7` (outline color) for new topics
  - Completed topics maintain default stroke with green number background

### 4. New Drawable Resources Created
- `bg_topic_number.xml` - White background for topic number (new/in-progress)
- `bg_topic_number_done.xml` - Green background for completed topic number
- `bg_status_new.xml` - Transparent background for "New" status
- `bg_status_reading.xml` - Yellow background for "Reading" status
- `bg_status_done.xml` - Green background for "Done" status
- `ic_chapter_default.xml` - Vector drawable for chapter icon

### 5. Code Updates

#### HomeFragment.java
- Updated to use `ImageView` instead of `TextView` for chapter icon
- Added logic to set chapter icon drawable
- Added imports for `ImageView` and `FrameLayout`
- Maintains all existing functionality (progress tracking, click handlers, etc.)

#### ChapterActivity.java
- Updated to use new `item_topic.xml` layout instead of `item_chapter.xml`
- Changed view references from chapter-specific to topic-specific IDs
- Added logic to:
  - Display topic number based on position in list
  - Set appropriate background color for topic number container
  - Show status badges (New/Done) based on completion
  - Apply correct stroke colors to cards
- Added import for `FrameLayout`
- Maintains all existing functionality (topic loading, click handlers, etc.)

## Design Specifications Implemented

### Home Fragment (Chapters)
✅ Icon changed from emoji to ImageView
✅ Maintains rounded background with stroke
✅ Progress bar and percentage display
✅ Badge showing completion percentage or "Done"
✅ Card stroke maintained

### Chapter List (Topics)
✅ Topic number displayed in rounded square
✅ Completed topics show green background on number
✅ Status badges (New/Done) displayed
✅ Card strokes maintained
✅ Time estimate display (hidden for now, can be populated when data available)
✅ Three-line description with ellipsis

## Color Scheme
All colors use the existing light mode color palette:
- Primary: `#F5A623` (amber/orange)
- Surface: `#FFFFFF` (white)
- Surface Variant: `#F5F5F0` (light beige)
- Outline: `#D3D1C7` (light gray)
- Badge Done: `#E1F5EE` (light green) with `#0F6E56` (dark green) text
- Badge Warn: `#FEF3DC` (light amber) with `#7A4D00` (dark amber) text

## Notes
- All existing functionality preserved (click handlers, progress tracking, etc.)
- Design changes are purely visual
- Light mode colors maintained as default
- Stroke widths kept consistent with existing design
- Topic "Reading" status can be implemented when backend tracking is added
