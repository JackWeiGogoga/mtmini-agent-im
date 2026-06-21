# Offline Packages

This directory stores local bootstrap packages used by `ansible/site.yml`.

Generated archives are intentionally ignored by Git. Create or refresh them with:

```bash
ansible/scripts/prepare-offline-packages.sh
```

Expected files:

- `node-v24.14.0-linux-x64.tar.xz`
- `nvm-v0.40.3.tar.gz`
- `oh-my-zsh.tar.gz`
- `zsh-autosuggestions.tar.gz`
- `zsh-syntax-highlighting.tar.gz`
- `SHA256SUMS`

