import re
import json
import os

def parse_markdown_to_json(md_file_path, output_json_path):
    """
    Convert markdown file to JSON format with chapters structure.
    
    Args:
        md_file_path: Path to the input markdown file
        output_json_path: Path to save the output JSON file
    """
    
    # Read the markdown file
    with open(md_file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Pattern 1: **CHAPTER X** followed by **TITLE** on next line
    # Pattern 2: **CHAPTER X - TITLE** or **CHAPTER X – TITLE** (with dash or em-dash)
    chapter_pattern = r'\*\*CHAPTER\s+(\d+)(?:\s*[-–]\s*([^*\n]+?))?\*\*(?:\s*\n\*\*([^*]+?)\*\*)?'
    
    # Find all chapter matches
    chapters = []
    chapter_matches = list(re.finditer(chapter_pattern, content))
    
    for i, match in enumerate(chapter_matches):
        chapter_num = int(match.group(1))
        
        # Get title from either format
        # Group 2: title after dash (Format 2: **CHAPTER X - TITLE**)
        # Group 3: title on next line (Format 1: **CHAPTER X**\n**TITLE**)
        chapter_title = (match.group(2) or match.group(3) or "").strip()
        
        # Get the start position of this chapter
        start_pos = match.start()
        
        # Get the end position (start of next chapter or end of file)
        if i < len(chapter_matches) - 1:
            end_pos = chapter_matches[i + 1].start()
        else:
            end_pos = len(content)
        
        # Extract chapter content
        chapter_content = content[start_pos:end_pos].strip()
        
        # Remove the chapter header from content (more flexible removal)
        # Remove the matched pattern
        chapter_content = chapter_content[match.end() - match.start():].strip()
        
        # Find images in the chapter content (looking for image references)
        image_pattern = r'!\[([^\]]*)\]\(([^)]+)\)'
        images = []
        for img_match in re.finditer(image_pattern, chapter_content):
            images.append({
                "alt": img_match.group(1),
                "path": img_match.group(2)
            })
        
        # Create chapter object with sequential topic IDs (all under ch01)
        chapter_obj = {
            "id": f"ch01_t{chapter_num:02d}",  # All topics under ch01
            "number": chapter_num,
            "title": f"**CHAPTER {chapter_num}**: **{chapter_title}**",
            "content": chapter_content,
            "images": images
        }
        
        chapters.append(chapter_obj)
    
    # Create the proper nested structure for the app
    result = {
        "chapters": [
            {
                "id": "ch01",
                "number": 1,
                "title": "Dream Pediatrics Textbook",
                "description": "Complete pediatrics textbook with examination techniques and procedures",
                "topics": []
            }
        ]
    }
    
    # Add all chapters as topics under the single chapter
    for chapter in chapters:
        topic = {
            "id": chapter["id"],
            "number": chapter["number"],
            "title": chapter["title"],
            "content": chapter["content"],  # Renamed to content for app compatibility
            "images": [img["path"] if isinstance(img, dict) else img for img in chapter["images"]]
        }
        result["chapters"][0]["topics"].append(topic)
    
    # Write to JSON file
    with open(output_json_path, 'w', encoding='utf-8') as f:
        json.dump(result, f, indent=2, ensure_ascii=False)
    
    print(f"✓ Conversion complete!")
    print(f"✓ Found {len(chapters)} topics")
    print(f"✓ All topics grouped under 1 chapter (ch01)")
    print(f"✓ Topic IDs: ch01_t01 to ch01_t{len(chapters):02d}")
    print(f"✓ Output saved to: {output_json_path}")
    
    return result


def main():
    # Get the script directory
    script_dir = os.path.dirname(os.path.abspath(__file__))
    
    # Define input and output paths
    input_file = os.path.join(script_dir, 'output', 'output.md')
    output_file = os.path.join(script_dir, 'output', 'output.json')
    
    # Check if input file exists
    if not os.path.exists(input_file):
        print(f"✗ Error: Input file not found: {input_file}")
        print("Please make sure 'output.md' exists in the 'output' folder")
        return
    
    # Convert markdown to JSON
    print(f"Converting {input_file} to JSON...")
    result = parse_markdown_to_json(input_file, output_file)
    
    # Print summary
    print("\nStructure:")
    print(f"  - 1 Chapter: {result['chapters'][0]['title']}")
    print(f"  - {len(result['chapters'][0]['topics'])} Topics")
    print("\nTopics:")
    for topic in result['chapters'][0]['topics'][:5]:  # Show first 5
        print(f"  - {topic['id']}: {topic['title'][:60]}...")
    if len(result['chapters'][0]['topics']) > 5:
        print(f"  ... and {len(result['chapters'][0]['topics']) - 5} more topics")


if __name__ == "__main__":
    main()
