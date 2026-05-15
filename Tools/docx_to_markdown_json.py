"""
DOCX to Markdown Converter
Converts DOCX files to Markdown format
Preserves: bold, italic, tables, bullet points, sub-bullets, inline images
"""

import os
from docx import Document
from docx.oxml.text.paragraph import CT_P
from docx.oxml.table import CT_Tbl
from docx.table import _Cell, Table
from docx.text.paragraph import Paragraph
import base64


class DocxToMarkdownConverter:
    def __init__(self, docx_path, output_dir="output"):
        self.docx_path = docx_path
        self.output_dir = output_dir
        self.images_dir = os.path.join(output_dir, "images")
        self.image_counter = 0
        
        # Create output directories
        os.makedirs(self.output_dir, exist_ok=True)
        os.makedirs(self.images_dir, exist_ok=True)
        
    def extract_images_from_docx(self, doc):
        """Extract all images from the DOCX file"""
        image_map = {}
        
        for rel_id, rel in doc.part.rels.items():
            # Skip external relationships
            if rel.is_external:
                continue
                
            if "image" in rel.target_ref:
                try:
                    self.image_counter += 1
                    image_data = rel.target_part.blob
                    
                    # Determine image extension
                    content_type = rel.target_part.content_type
                    ext_map = {
                        'image/png': 'png',
                        'image/jpeg': 'jpg',
                        'image/jpg': 'jpg',
                        'image/gif': 'gif',
                        'image/bmp': 'bmp'
                    }
                    ext = ext_map.get(content_type, 'png')
                    
                    # Save image
                    image_filename = f"image_{self.image_counter}.{ext}"
                    image_path = os.path.join(self.images_dir, image_filename)
                    
                    with open(image_path, 'wb') as img_file:
                        img_file.write(image_data)
                    
                    # Store relative path for markdown using rel_id
                    image_map[rel_id] = f"images/{image_filename}"
                except Exception as e:
                    print(f"Warning: Could not extract image {rel_id}: {e}")
                    continue
                
        return image_map
    
    def process_run_formatting(self, run):
        """Convert run formatting to markdown"""
        text = run.text
        if not text:
            return ""
        
        # Apply formatting
        if run.bold and run.italic:
            return f"***{text}***"
        elif run.bold:
            return f"**{text}**"
        elif run.italic:
            return f"*{text}*"
        elif run.underline:
            return f"<u>{text}</u>"
        elif run.font.strike:
            return f"~~{text}~~"
        else:
            return text
    
    def process_paragraph(self, paragraph, image_map):
        """Convert paragraph to markdown with formatting"""
        # Handle images in paragraph
        markdown_text = ""
        
        for run in paragraph.runs:
            # Check for inline images
            if run._element.xpath('.//pic:pic'):
                # Try to find image reference
                for drawing in run._element.xpath('.//a:blip'):
                    embed = drawing.get('{http://schemas.openxmlformats.org/officeDocument/2006/relationships}embed')
                    if embed and embed in image_map:
                        markdown_text += f"![image]({image_map[embed]})"
                    else:
                        # Image not found in map, skip or add placeholder
                        markdown_text += "[Image]"
            else:
                markdown_text += self.process_run_formatting(run)
        
        # Handle paragraph styles
        style = paragraph.style.name.lower() if paragraph.style else ""
        
        # Check if this is a bold title (entire paragraph is bold)
        is_bold_title = all(run.bold for run in paragraph.runs if run.text.strip())
        
        # Headings - no extra spacing (handled in main convert method)
        if 'heading 1' in style:
            return f"# {markdown_text}\n"
        elif 'heading 2' in style:
            return f"## {markdown_text}\n"
        elif 'heading 3' in style:
            return f"### {markdown_text}\n"
        elif 'heading 4' in style:
            return f"#### {markdown_text}\n"
        elif 'heading 5' in style:
            return f"##### {markdown_text}\n"
        elif 'heading 6' in style:
            return f"###### {markdown_text}\n"
        
        # Lists
        if paragraph._element.xpath('.//w:numPr'):
            # Numbered list
            level = self.get_list_level(paragraph)
            indent = "   " * level
            return f"{indent}1. {markdown_text}\n"
        elif paragraph._element.xpath('.//w:pPr/w:numPr') or \
             any(run.text.strip().startswith(('•', '-', '*')) for run in paragraph.runs if run.text):
            # Bullet list
            level = self.get_list_level(paragraph)
            indent = "   " * level
            # Remove bullet if it's already in text
            clean_text = markdown_text.lstrip('•-* ')
            return f"{indent}- {clean_text}\n"
        
        # Regular paragraph
        if markdown_text.strip():
            # If it's a bold title, single newline; otherwise double newline
            if is_bold_title:
                return f"{markdown_text}\n"
            else:
                return f"{markdown_text}\n\n"
        else:
            return "\n"
    
    def get_list_level(self, paragraph):
        """Determine the indentation level of a list item"""
        try:
            num_pr = paragraph._element.xpath('.//w:numPr/w:ilvl')
            if num_pr:
                return int(num_pr[0].get('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}val', 0))
        except:
            pass
        return 0
    
    def process_table(self, table):
        """Convert table to markdown format"""
        markdown_table = "\n"
        
        for i, row in enumerate(table.rows):
            # Process cells
            cells = []
            for cell in row.cells:
                cell_text = ""
                for paragraph in cell.paragraphs:
                    for run in paragraph.runs:
                        cell_text += self.process_run_formatting(run)
                cells.append(cell_text.strip().replace('\n', ' '))
            
            # Add row
            markdown_table += "| " + " | ".join(cells) + " |\n"
            
            # Add header separator after first row
            if i == 0:
                markdown_table += "| " + " | ".join(["---"] * len(cells)) + " |\n"
        
        return markdown_table + "\n"
    
    def convert_to_markdown(self):
        """Main conversion method"""
        doc = Document(self.docx_path)
        
        # Extract images
        image_map = self.extract_images_from_docx(doc)
        
        markdown_content = ""
        previous_was_bold_title = False
        
        # Process document elements
        for element in doc.element.body:
            if isinstance(element, CT_P):
                # Paragraph
                paragraph = Paragraph(element, doc)
                
                # Check if current paragraph is a bold title
                is_bold_title = all(run.bold for run in paragraph.runs if run.text.strip()) and any(run.text.strip() for run in paragraph.runs)
                
                # Add spacing before bold titles (but not if previous was also bold title)
                if is_bold_title and not previous_was_bold_title and markdown_content.strip():
                    markdown_content += "\n"
                
                markdown_content += self.process_paragraph(paragraph, image_map)
                previous_was_bold_title = is_bold_title
                
            elif isinstance(element, CT_Tbl):
                # Table
                table = Table(element, doc)
                markdown_content += self.process_table(table)
                previous_was_bold_title = False
        
        return markdown_content
    

    
    def save_as_markdown(self, markdown_content, output_filename="output.md"):
        """Save as plain markdown file"""
        output_path = os.path.join(self.output_dir, output_filename)
        with open(output_path, 'w', encoding='utf-8') as f:
            f.write(markdown_content)
        
        return output_path
    
    def convert(self):
        """Convert DOCX to Markdown and save"""
        print(f"Converting {self.docx_path}...")
        
        # Convert to markdown
        markdown_content = self.convert_to_markdown()
        
        # Save as Markdown
        md_path = self.save_as_markdown(markdown_content)
        print(f"✓ Markdown saved to: {md_path}")
        
        print(f"✓ Extracted {self.image_counter} images to: {self.images_dir}")
        
        return md_path


def main():
    # Configuration
    docx_file = "Document1.docx"
    output_directory = "output"
    
    # Check if file exists
    if not os.path.exists(docx_file):
        print(f"Error: {docx_file} not found!")
        return
    
    # Convert
    converter = DocxToMarkdownConverter(docx_file, output_directory)
    result = converter.convert()
    
    print("\n✅ Conversion complete!")
    print(f"Markdown file: {result}")


if __name__ == "__main__":
    main()
