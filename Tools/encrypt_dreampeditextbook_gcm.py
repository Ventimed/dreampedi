# encrypt_textbook_aesgcm.py
import os
import json
import base64
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

def write_file(path, data, mode='wb'):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, mode) as f:
        f.write(data)

def main():
    # Get the script directory
    script_dir = os.path.dirname(os.path.abspath(__file__))
    
    # Input: output.json from the output folder
    input_json_path = os.path.join(script_dir, "output", "output.json")
    
    # Output: encrypted folder inside output
    out_dir = os.path.join(script_dir, "output", "encrypted")
    
    # Check if input file exists
    if not os.path.exists(input_json_path):
        print(f"✗ Error: Input file not found: {input_json_path}")
        print("Please run md_to_json.py first to generate output.json")
        return

    # read JSON plaintext
    with open(input_json_path, "rb") as f:
        plaintext = f.read()

    # generate random AES-256 key (32 bytes)
    aes_key = AESGCM.generate_key(bit_length=256)
    aesgcm = AESGCM(aes_key)

    # random 12-byte nonce
    nonce = os.urandom(12)

    # encrypt: returns ciphertext || tag (AESGCM implementation appends auth tag to ciphertext)
    ciphertext_and_tag = aesgcm.encrypt(nonce, plaintext, None)

    # Write textbook.enc as: nonce(12) + ciphertext_and_tag
    enc_path = os.path.join(out_dir, "textbook.enc")
    write_file(enc_path, nonce + ciphertext_and_tag)

    # Write metadata.json (human-friendly)
    metadata = {
        "alg": "AES-GCM",
        "version": 1,
        "nonce_b64": base64.b64encode(nonce).decode("utf-8"),
        "cipher": "nonce||ciphertext||tag",
        "key_length": 256
    }
    meta_path = os.path.join(out_dir, "metadata.json")
    write_file(meta_path, json.dumps(metadata, indent=2).encode("utf-8"))

    # Write AES key (raw binary) to a secure file (DO NOT upload to public storage)
    key_path = os.path.join(out_dir, "aes_key.bin")
    write_file(key_path, aes_key)

    print("\n✓ Encryption complete!")
    print(f"✓ Input file: {input_json_path}")
    print(f"✓ Output directory: {out_dir}")
    print(f"\nGenerated files:")
    print(f"  1. {enc_path}")
    print(f"  2. {meta_path}")
    print(f"  3. {key_path}")
    print(f"\n⚠️  IMPORTANT SECURITY NOTES:")
    print(f"  ✓ Upload textbook.enc and metadata.json to Firebase Storage (PUBLIC)")
    print(f"  ✗ NEVER upload aes_key.bin to public storage!")
    print(f"  ✓ Store aes_key.bin in private bucket (gs://dream-pedi-secrets/)")
    print(f"\nNext steps:")
    print(f"  1. Upload textbook.enc to: gs://dream-pedi/textbooks/")
    print(f"  2. Upload metadata.json to: gs://dream-pedi/textbooks/")
    print(f"  3. Upload aes_key.bin to: gs://dream-pedi-secrets/")

if __name__ == "__main__":
    main()
