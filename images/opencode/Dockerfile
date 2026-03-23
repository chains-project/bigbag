# -----------------------------------------------------------------------------
# STAGE 1: BASE IMAGE WITH JAVA AND MAVEN
# -----------------------------------------------------------------------------
FROM maven:3.9.2-eclipse-temurin-17

USER root

# -----------------------------------------------------------------------------
# STAGE 2: INSTALL NODE.JS AND SYSTEM TOOLS
# -----------------------------------------------------------------------------
# Node.js is required by OpenCode CLI. gh CLI is required for GitHub Copilot OAuth authentication
# (Copilot API does not accept Personal Access Tokens — only OAuth tokens via gh auth login).
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg \
        | dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
        | tee /etc/apt/sources.list.d/github-cli.list > /dev/null && \
    apt-get update && apt-get install -y \
    nodejs \
    gh \
    git \
    curl \
    zsh \
    tmux \
    procps \
    vim \
    && rm -rf /var/lib/apt/lists/*

# -----------------------------------------------------------------------------
# STAGE 3: INSTALL OPENCODE CLI
# -----------------------------------------------------------------------------
RUN npm install -g opencode-ai

# -----------------------------------------------------------------------------
# STAGE 4: DOCKER ENVIRONMENT CONFIGURATION
# -----------------------------------------------------------------------------
# Set shell so OpenCode can execute commands non-interactively
ENV SHELL=/bin/zsh

# Ensure binaries installed by npm are on the PATH
ENV PATH="/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin:${PATH}"

# -----------------------------------------------------------------------------
# STAGE 5: SHELL THEME (Oh My Zsh)
# -----------------------------------------------------------------------------
RUN sh -c "$(curl -fsSL https://raw.githubusercontent.com/ohmyzsh/ohmyzsh/master/tools/install.sh)" "" --unattended \
    && sed -i 's/ZSH_THEME=.*/ZSH_THEME="agnoster"/' /root/.zshrc

WORKDIR /workspace

CMD ["zsh"]
