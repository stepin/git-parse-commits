FROM stepin/kotlin-scripting

RUN apt-get update \
&& apt-get -y install jc git \
&& rm -rf /var/lib/apt/lists/*

COPY git-parse-commits.main.kts ./

# Cache dependencies and compilation result for better start-up speed
ENV KOTLIN_MAIN_KTS_COMPILED_SCRIPTS_CACHE_DIR /app
RUN /app/git-parse-commits.main.kts version

ENTRYPOINT ["/app/git-parse-commits.main.kts"]
