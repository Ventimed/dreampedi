# Subscribe Card Color & Styling Update

## Changes Made to Match Screenshot

### 1. Background Color
Updated the subscribe card background to match the screenshot exactly:
- **New Color**: `#FFEFD5` (Papaya Whip - light peachy cream)
- **Previous**: `#FEF3DC` (too yellow) → `#FBE4B8` (too dark) → `#FFEFD5` (perfect match)
- Applied to both light and dark modes for consistency

### 2. Button Styling
Updated the Subscribe/Download button to match the screenshot:
- **Background**: White (`#FFFFFF`)
- **Text Color**: Dark brown (`@color/on_primary_dark` - `#5A3700`)
- **Corner Radius**: `10dp` (slightly more rounded)
- **Padding**: Increased to `24dp` horizontal for better proportion
- **Elevation**: Set to `0dp` for flat appearance
- **Text Size**: `15sp` (slightly larger)

### 3. Text Styling
Updated text colors and sizes:
- **Subtitle** ("Unlock full access"):
  - Size: `13sp`
  - Color: Dark brown with 70% opacity
  
- **Title** ("Subscribe to all chapters"):
  - Size: `16sp`
  - Color: Dark brown (full opacity)
  - Style: Bold

### 4. Icon Styling
- **Crown Icon**: Dark brown tint applied via layout
- **Download Icon**: Dark brown tint applied via layout
- **Size**: `32dp` for both icons
- **Margin**: `12dp` end margin

### 5. Card Styling
- **Padding**: `16dp` all around
- **Corner Radius**: `12dp` (from bg_subscribe_card.xml)
- **Background**: Light peachy cream that blends with orange header

## Visual Result

The subscribe card now perfectly matches the screenshot with:
- ✅ Light peachy cream background
- ✅ White button with dark brown text
- ✅ Dark brown crown icon
- ✅ Proper text hierarchy and sizing
- ✅ Smooth rounded corners
- ✅ Flat design (no elevation)

## Color Palette Used

```xml
<!-- Subscribe Card -->
<color name="subscribe_card_bg">#FFEFD5</color>      <!-- Light peachy cream -->
<color name="on_primary_dark">#5A3700</color>         <!-- Dark brown text/icons -->
<color name="button_bg">#FFFFFF</color>               <!-- White button -->
```

## Files Modified

1. `app/src/main/res/values/colors.xml` - Updated subscribe_card_bg color
2. `app/src/main/res/values-night/colors.xml` - Updated subscribe_card_bg color
3. `app/src/main/res/layout/fragment_home.xml` - Updated button and text styling
4. `app/src/main/res/drawable/ic_crown.xml` - Changed to use tint instead of hardcoded color
5. `app/src/main/res/drawable/ic_download.xml` - Changed to use tint instead of hardcoded color

## Build Status

✅ **Build successful** - All changes compiled without errors
