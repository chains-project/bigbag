# -----------------------------------------------------------------------------
# STAGE 1: BASE IMAGE WITH JAVA 11 AND MAVEN (Debian Bullseye)
# -----------------------------------------------------------------------------
    FROM maven:3.9.2-eclipse-temurin-11

    USER root
    
    # -----------------------------------------------------------------------------
    # STAGE 2: INSTALL NODE.JS AND TOOLS
    # -----------------------------------------------------------------------------
    # Install Node.js (required for Gemini CLI) and system tools
    RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
        apt-get update && apt-get install -y \
        nodejs \
        git \
        curl \
        zsh \
        tmux \
        procps \
        vim \
        && rm -rf /var/lib/apt/lists/*
    
    # -----------------------------------------------------------------------------
    # STAGE 3: INSTALL GEMINI CLI (NPM)
    # -----------------------------------------------------------------------------
    RUN npm install -g @google/gemini-cli
    
    # -----------------------------------------------------------------------------
    # STAGE 4: CRITICAL DOCKER CONFIGURATION
    # -----------------------------------------------------------------------------
    # 1. Disable Sandbox because we are already inside a container
    ENV GEMINI_SANDBOX=false
    
    # 2. Define the shell so Gemini can execute YOLO commands
    ENV SHELL=/bin/zsh
    
    # 3. Force PATH
    ENV PATH="/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin:${PATH}"
    
    # -----------------------------------------------------------------------------
    # STAGE 5: SHELL AESTHETICS (Oh My Zsh)
    # -----------------------------------------------------------------------------
    RUN sh -c "$(curl -fsSL https://raw.githubusercontent.com/ohmyzsh/ohmyzsh/master/tools/install.sh)" "" --unattended \
        && sed -i 's/ZSH_THEME=.*/ZSH_THEME="agnoster"/' /root/.zshrc
    
    WORKDIR /work
    
    # Start with Zsh
    CMD ["zsh"]