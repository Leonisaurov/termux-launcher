# Scripts

## xsel
A clipboard tool compatible with xsel(1) that uses Android's native clipboard
via a ContentProvider embedded in the Termux app.

### Install
```bash
mkdir -p ~/.local/bin
cp scripts/bin/xsel ~/.local/bin/
chmod +x ~/.local/bin/xsel
# Ensure ~/.local/bin is in your PATH
```

### Usage
```bash
# Read clipboard
xsel -o

# Set clipboard from stdin
echo "hello" | xsel -i

# Clear clipboard
xsel -c
```
