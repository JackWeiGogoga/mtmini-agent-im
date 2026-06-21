# Waimaidashi Server Initialization

This Ansible setup initializes new OpenCloudOS/RHEL-compatible servers for the Waimaidashi deployment.

It is based on the current production environment observed on 2026-06-21:

- OS: OpenCloudOS 8.10
- Java: OpenJDK 17
- Node.js: 24.14.0 via root nvm
- Shell: zsh with Oh My Zsh, zsh-autosuggestions, and zsh-syntax-highlighting for root
- Caddy: 2.6.4 on app/edge nodes
- MySQL: 8.4.x on the data node
- Redis: 5.0.x on the data node
- Standard paths:
  - `/opt/waimaidashi/backend`
  - `/etc/waimaidashi`
  - `/var/www/mt-admin`
  - `/var/www/mt-ai-promotion`

## Files

- `inventory.example.ini`: example inventory for one app server and one data server.
- `group_vars/all.yml`: shared variables, packages, users, and directories.
- `site.yml`: base initialization playbook.
- `scripts/prepare-offline-packages.sh`: downloads local bootstrap packages.
- `files/offline/`: ignored directory for generated offline packages.

## Usage

Install Ansible locally:

```bash
brew install ansible
```

Prepare the lightweight offline packages first. This avoids pulling nvm, Node.js, Oh My Zsh, and zsh plugins from GitHub on the server:

```bash
ansible/scripts/prepare-offline-packages.sh
```

Copy the example inventory and edit the target hosts:

```bash
cp ansible/inventory.example.ini ansible/inventory.ini
```

The current recommended role mapping is:

```ini
[app]
mt4

[data]
mt3
```

Run a connectivity check:

```bash
ansible -i ansible/inventory.ini all -m ping
```

Run the initialization:

```bash
ansible-playbook -i ansible/inventory.ini ansible/site.yml
```

`site.yml` bootstraps `python3.11` first because OpenCloudOS 8 ships with Python 3.6, while modern Ansible requires a newer remote interpreter.

To temporarily use online GitHub/nvm installation instead of local offline packages:

```bash
ansible-playbook -i ansible/inventory.ini ansible/site.yml -e offline_packages_enabled=false
```

Limit to one role when needed:

```bash
ansible-playbook -i ansible/inventory.ini ansible/site.yml --limit app
ansible-playbook -i ansible/inventory.ini ansible/site.yml --limit data
```

## Notes

This playbook does not deploy application jars, frontend assets, Caddy virtual hosts, MySQL users, Redis passwords, or `/etc/waimaidashi/backend.env`.

Keep secrets out of Git. Configure production service credentials separately, preferably through an encrypted inventory, Ansible Vault, or a controlled deployment step.
