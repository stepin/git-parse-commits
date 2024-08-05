FROM stepin/kotlin-scripting

# Install pipx for jc and git
USER root
RUN apt-get update \
&& apt-get -y install pipx git \
&& rm -rf /var/lib/apt/lists/*
RUN git config --system --add safe.directory '*'
USER app

# Install jc
RUN pipx ensurepath \
    && pipx install jc
ENV PATH=$PATH:/app/.local/bin/

COPY git-parse-commits git-parse-commits.main.kts ./
# Cache libraries and compilation result
RUN /app/git-parse-commits.main.kts version && find /app/.m2 -type f -exec chmod 644 {} \;

ENTRYPOINT ["/app/git-parse-commits.main.kts"]
