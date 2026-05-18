# Visual Changes Guide

## Before & After Comparison

### Home Fragment - Chapter Items

**BEFORE:**
```
┌─────────────────────────────────────────┐
│  🩺  Chapter Title            8%        │
│      Chapter description                │
│      ━━━━━━━━━━━━━━━━━━━━━━  2 / 24    │
└─────────────────────────────────────────┘
```

**AFTER:**
```
┌─────────────────────────────────────────┐
│  [📄] Chapter Title           8%        │
│       Chapter description               │
│       ━━━━━━━━━━━━━━━━━━━━━━  2 / 24   │
└─────────────────────────────────────────┘
```
- Icon changed from emoji (🩺) to ImageView with vector drawable
- Same layout structure maintained
- Progress bar and badge remain unchanged

---

### Chapter List - Topic Items

**BEFORE (using item_chapter.xml):**
```
┌─────────────────────────────────────────┐
│  🩺  Topic Title              8%        │
│      Topic description                  │
│      ━━━━━━━━━━━━━━━━━━━━━━            │
└─────────────────────────────────────────┘
```

**AFTER (using new item_topic.xml):**
```
┌─────────────────────────────────────────┐
│  ┌──┐                                   │
│  │26│  Lumbar puncture                  │
│  └──┘  Indications, positioning, CSF    │
│        collection & interpretation      │
│        ⏱ 9 min          Reading         │
└─────────────────────────────────────────┘
```

**For Completed Topics:**
```
┌─────────────────────────────────────────┐
│  ┌──┐                                   │
│  │✓ │  Peripheral IV line insertion     │
│  └──┘  Site selection, equipment prep   │
│        insertion technique              │
│        ⏱ 6 min          Done            │
└─────────────────────────────────────────┘
```
- Topic number displayed in rounded square
- Completed topics: green background on number
- Status badges: "New", "Reading", or "Done"
- Time estimate shown
- No progress bar (replaced with status)

---

## Stroke Colors

### Chapter Items (Home Fragment)
- **Default:** Light gray (`#D3D1C7`)
- **All states:** Same stroke color

### Topic Items (Chapter List)
- **New:** Light gray stroke (`#D3D1C7`)
- **In Progress:** Yellow/amber stroke (`#F5A623`) - *to be implemented with backend tracking*
- **Completed:** Light gray stroke (`#D3D1C7`) with green number background

---

## Color Reference

| Element | State | Background | Text Color | Stroke |
|---------|-------|------------|------------|--------|
| Chapter Icon Container | All | `#FEF3DC` (light amber) | - | - |
| Topic Number | New/Reading | `#FFFFFF` (white) | `#1A1A18` (dark) | - |
| Topic Number | Done | `#E1F5EE` (light green) | `#0F6E56` (dark green) | - |
| Status Badge "New" | - | Transparent | `#888780` (gray) | - |
| Status Badge "Reading" | - | `#FEF3DC` (light amber) | `#7A4D00` (dark amber) | - |
| Status Badge "Done" | - | `#E1F5EE` (light green) | `#0F6E56` (dark green) | - |
| Card Stroke | Default | - | - | `#D3D1C7` (light gray) |
| Card Stroke | In Progress | - | - | `#F5A623` (amber) |

---

## Implementation Status

✅ **Completed:**
- Home fragment icon changed to ImageView
- New topic item layout created
- Topic number display implemented
- Status badges created
- Stroke colors defined
- Completed topic styling implemented

⏳ **Future Enhancement:**
- "Reading" status detection (requires backend tracking)
- Yellow stroke for in-progress topics (requires backend tracking)
- Time estimate population (when data available)
- Custom icons per chapter (optional)

---

## Files Modified

### Layout Files
- `item_chapter.xml` - Updated icon from TextView to ImageView
- `item_topic.xml` - **NEW** - Topic list item layout

### Drawable Resources (NEW)
- `ic_chapter_default.xml` - Chapter icon vector drawable
- `bg_topic_number.xml` - Topic number background (white)
- `bg_topic_number_done.xml` - Completed topic number background (green)
- `bg_status_new.xml` - "New" status badge background
- `bg_status_reading.xml` - "Reading" status badge background
- `bg_status_done.xml` - "Done" status badge background

### Color Resources
- Added `stroke_in_progress` color (`#F5A623`)

### Java Files
- `HomeFragment.java` - Updated to use ImageView for chapter icon
- `ChapterActivity.java` - Updated to use new topic layout and styling logic
