# -----------------------------------------------------------------------------
# ETAPA 1: BASE CON JAVA 8 Y MAVEN (Debian Bullseye)
# -----------------------------------------------------------------------------
    FROM maven:3.9.6-eclipse-temurin-17

    USER root
    
    # -----------------------------------------------------------------------------
    # ETAPA 2: INSTALACIÓN DE NODE.JS Y HERRAMIENTAS
    # -----------------------------------------------------------------------------
    # Instalamos Node.js (necesario para Gemini CLI) y herramientas de sistema
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
    # ETAPA 3: INSTALACIÓN DE GEMINI CLI (NPM)
    # -----------------------------------------------------------------------------
    RUN npm install -g @google/gemini-cli
    
    # -----------------------------------------------------------------------------
    # ETAPA 4: CONFIGURACIÓN CRÍTICA PARA DOCKER
    # -----------------------------------------------------------------------------
    # 1. Desactivamos el Sandbox porque ya estamos dentro de un contenedor
    ENV GEMINI_SANDBOX=false
    
    # 2. Definimos el Shell para que Gemini pueda ejecutar comandos YOLO
    ENV SHELL=/bin/zsh
    
    # 3. Forzamos el PATH
    ENV PATH="/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin:${PATH}"
    
    # -----------------------------------------------------------------------------
    # ETAPA 5: ESTÉTICA (Oh My Zsh)
    # -----------------------------------------------------------------------------
    RUN sh -c "$(curl -fsSL https://raw.githubusercontent.com/ohmyzsh/ohmyzsh/master/tools/install.sh)" "" --unattended \
        && sed -i 's/ZSH_THEME=.*/ZSH_THEME="agnoster"/' /root/.zshrc
    
    WORKDIR /work
    
    # Iniciamos con Zsh
    CMD ["zsh"]