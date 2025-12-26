# -----------------------------------------------------------------------------
# ETAPA 1: IMAGEN BASE
# Partimos de la imagen específica de tu benchmark, que está basada en Alpine.
# -----------------------------------------------------------------------------
    FROM ghcr.io/chains-project/breaking-updates:base-image

    # -----------------------------------------------------------------------------
    # ETAPA 2: CONFIGURACIÓN E INSTALACIÓN DE PAQUETES
    # -----------------------------------------------------------------------------
    # Cambiamos a usuario 'root' para tener permisos de instalación.
    USER root
    
    # --- Instalación de Dependencias del Sistema con APK ---
    # Usamos 'apk', el gestor de paquetes de Alpine Linux.
    # '--no-cache' es una buena práctica para mantener la imagen pequeña.
    #
    # - nodejs-current y npm: Instala la última versión estable de Node.js (v20+)
    #   y su gestor de paquetes. ESTO ARREGLA EL 'SyntaxError'.
    # - python3 y py3-pip: Para instalar el agente de Python.
    # - diffutils: Para poder usar el comando 'diff'.
    #
    RUN apk add --no-cache \
        nodejs-current \
        npm \
        python3 \
        py3-pip \
        diffutils
    
    # -----------------------------------------------------------------------------
    # ETAPA 3: INSTALACIÓN DE LAS HERRAMIENTAS CLI DE GEMINI
    # -----------------------------------------------------------------------------
    
    # --- Instala el CLI de Gemini (versión NPM de Node.js) ---
    # Este es el cliente de API que solicitaste instalar.
    # El flag -g lo instala de forma global.
    RUN npm install -g @google/gemini-cli --unsafe-perm
    
    # --- Instala el CLI de Gemini (versión PIP de Python) ---
    # Este es el agente experimental que puede ejecutar herramientas locales.
    # Lo incluimos para que la imagen sea completa y versátil.
    RUN pip3 install --no-cache-dir google-generativeai
    
    # -----------------------------------------------------------------------------
    # ETAPA 4: CONFIGURACIÓN DEL ENTORNO
    # -----------------------------------------------------------------------------
    
    # --- Arregla el PATH para el agente de Python ---
    # ESTA LÍNEA ES CRUCIAL. Arregla el error "gemini: not found" para el
    # agente de Python, añadiendo su directorio de instalación al PATH del sistema.
    ENV PATH="/root/.local/bin:${PATH}"
    
    # Establecemos el directorio de trabajo por defecto.
    WORKDIR /work
    
    # -----------------------------------------------------------------------------
    # ETAPA 5: COMANDO POR DEFECTO
    # -----------------------------------------------------------------------------
    # Define qué comando se ejecutará si se inicia el contenedor sin argumentos.
    # Iniciar un shell es lo más útil para la exploración interactiva.
    CMD ["/bin/sh"]