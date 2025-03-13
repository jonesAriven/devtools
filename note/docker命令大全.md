# Docker 命令大全

## 目录

- [基础命令](#基础命令)
- [镜像管理](#镜像管理)
- [容器管理](#容器管理)
- [网络管理](#网络管理)
- [数据卷管理](#数据卷管理)
- [Docker Compose](#docker-compose)
- [Docker Swarm](#docker-swarm)
- [系统信息](#系统信息)
- [常见问题FAQ](#常见问题faq)

## 基础命令

### docker version

**说明**：显示 Docker 客户端和服务器版本信息

**语法**：`docker version`

**示例**：
```bash
docker version
```

### docker info

**说明**：显示 Docker 系统信息，包括镜像和容器数量

**语法**：`docker info`

**示例**：
```bash
docker info
```

### docker help

**说明**：显示 Docker 命令帮助信息

**语法**：`docker help [命令]`

**示例**：
```bash
docker help
docker help run
```

## 镜像管理

### docker images

**说明**：列出本地镜像

**语法**：`docker images [OPTIONS] [REPOSITORY[:TAG]]`

**常用选项**：
- `-a, --all`：显示所有镜像（默认隐藏中间镜像）
- `-q, --quiet`：只显示镜像ID
- `--digests`：显示摘要信息
- `--no-trunc`：不截断输出

**示例**：
```bash
docker images
docker images -a
docker images -q
docker images nginx
```

### docker pull

**说明**：从镜像仓库拉取镜像

**语法**：`docker pull [OPTIONS] NAME[:TAG|@DIGEST]`

**常用选项**：
- `-a, --all-tags`：下载仓库中的所有标签镜像
- `--disable-content-trust`：跳过镜像验证（默认为true）
- `--platform`：设置平台，如 linux/amd64, linux/arm64, windows/amd64

**示例**：
```bash
docker pull ubuntu:20.04
docker pull nginx:latest
docker pull --all-tags nginx
```

### docker search

**说明**：在 Docker Hub 中搜索镜像

**语法**：`docker search [OPTIONS] TERM`

**常用选项**：
- `--filter, -f`：根据提供的条件过滤输出
- `--limit`：最大搜索结果数（默认25）
- `--no-trunc`：不截断输出

**示例**：
```bash
docker search nginx
docker search --filter=stars=100 nginx
docker search --limit 10 ubuntu
```

### docker rmi

**说明**：删除本地一个或多个镜像

**语法**：`docker rmi [OPTIONS] IMAGE [IMAGE...]`

**常用选项**：
- `-f, --force`：强制删除
- `--no-prune`：不删除未标记的父镜像

**示例**：
```bash
docker rmi nginx:latest
docker rmi -f nginx:latest
docker rmi $(docker images -q)
```

### docker build

**说明**：从 Dockerfile 构建镜像

**语法**：`docker build [OPTIONS] PATH | URL | -`

**常用选项**：
- `-t, --tag`：镜像的名称和标签，格式为 name:tag
- `-f, --file`：指定 Dockerfile 路径（默认为 PATH/Dockerfile）
- `--no-cache`：构建镜像时不使用缓存
- `--pull`：始终尝试拉取最新的镜像
- `--build-arg`：设置构建时变量

**示例**：
```bash
docker build -t myapp:1.0 .
docker build -f Dockerfile.dev -t myapp:dev .
docker build --no-cache -t myapp:latest .
docker build --build-arg VERSION=1.0 -t myapp:1.0 .
```

### docker tag

**说明**：为镜像添加一个新的标签

**语法**：`docker tag SOURCE_IMAGE[:TAG] TARGET_IMAGE[:TAG]`

**示例**：
```bash
docker tag nginx:latest mynginx:v1
docker tag ubuntu:18.04 myregistry.com/myubuntu:18.04
```

### docker save

**说明**：将一个或多个镜像保存为 tar 归档文件

**语法**：`docker save [OPTIONS] IMAGE [IMAGE...]`

**常用选项**：
- `-o, --output`：写入到文件，而非标准输出

**示例**：
```bash
docker save -o nginx.tar nginx:latest
docker save ubuntu:18.04 > ubuntu.tar
```

### docker load

**说明**：从 tar 归档文件加载镜像

**语法**：`docker load [OPTIONS]`

**常用选项**：
- `-i, --input`：从文件读取，而非标准输入
- `-q, --quiet`：抑制加载输出

**示例**：
```bash
docker load -i nginx.tar
docker load < ubuntu.tar
```

### docker history

**说明**：显示镜像的历史

**语法**：`docker history [OPTIONS] IMAGE`

**常用选项**：
- `--format`：使用Go模板格式化输出
- `--no-trunc`：不截断输出
- `-q, --quiet`：只显示ID

**示例**：
```bash
docker history nginx:latest
docker history --no-trunc ubuntu:18.04
```

## 容器管理

### docker run

**说明**：创建并启动一个新容器

**语法**：`docker run [OPTIONS] IMAGE [COMMAND] [ARG...]`

**常用选项**：
- `-d, --detach`：后台运行容器
- `-i, --interactive`：即使没有连接，也保持STDIN打开
- `-t, --tty`：分配一个伪终端
- `-p, --publish`：将容器端口映射到主机
- `-v, --volume`：绑定挂载卷
- `--name`：为容器指定名称
- `--network`：连接容器到网络
- `-e, --env`：设置环境变量
- `--rm`：容器退出时自动删除
- `--restart`：容器退出时的重启策略

**示例**：
```bash
docker run -d nginx
docker run -it ubuntu bash
docker run -d -p 80:80 nginx
docker run -d --name my-nginx -p 80:80 -v /data:/usr/share/nginx/html nginx
docker run -d --restart=always nginx
```

### docker ps

**说明**：列出容器

**语法**：`docker ps [OPTIONS]`

**常用选项**：
- `-a, --all`：显示所有容器（默认只显示运行中的）
- `-f, --filter`：根据条件过滤输出
- `-n, --last`：显示最后创建的n个容器
- `-q, --quiet`：只显示容器ID
- `-s, --size`：显示容器文件大小

**示例**：
```bash
docker ps
docker ps -a
docker ps -q
docker ps -f status=running
```

### docker start/stop/restart

**说明**：启动/停止/重启一个或多个容器

**语法**：
- `docker start [OPTIONS] CONTAINER [CONTAINER...]`
- `docker stop [OPTIONS] CONTAINER [CONTAINER...]`
- `docker restart [OPTIONS] CONTAINER [CONTAINER...]`

**常用选项**：
- `-a, --attach`：附加STDOUT/STDERR（仅适用于start）
- `-i, --interactive`：附加STDIN（仅适用于start）
- `-t, --time`：等待停止的秒数（仅适用于stop）

**示例**：
```bash
docker start my-container
docker stop my-container
docker restart my-container
docker stop $(docker ps -q)
```

### docker exec

**说明**：在运行的容器中执行命令

**语法**：`docker exec [OPTIONS] CONTAINER COMMAND [ARG...]`

**常用选项**：
- `-d, --detach`：后台运行命令
- `-i, --interactive`：即使没有连接，也保持STDIN打开
- `-t, --tty`：分配一个伪终端
- `-e, --env`：设置环境变量
- `-w, --workdir`：容器内的工作目录

**示例**：
```bash
docker exec -it my-container bash
docker exec my-container ls -la
docker exec -w /var/www my-container pwd
```

### docker logs

**说明**：获取容器的日志

**语法**：`docker logs [OPTIONS] CONTAINER`

**常用选项**：
- `-f, --follow`：跟踪日志输出
- `--since`：显示自某个时间以来的日志
- `--until`：显示直到某个时间的日志
- `--tail`：从日志末尾显示的行数
- `-t, --timestamps`：显示时间戳

**示例**：
```bash
docker logs my-container
docker logs -f my-container
docker logs --tail 100 my-container
docker logs --since 2020-01-01T00:00:00 my-container
```

### docker rm

**说明**：删除一个或多个容器

**语法**：`docker rm [OPTIONS] CONTAINER [CONTAINER...]`

**常用选项**：
- `-f, --force`：强制删除运行中的容器
- `-v, --volumes`：删除与容器关联的匿名卷
- `-l, --link`：删除指定的链接

**示例**：
```bash
docker rm my-container
docker rm -f my-container
docker rm $(docker ps -aq)
docker rm -f $(docker ps -aq)
```

### docker inspect

**说明**：返回容器或镜像的详细信息

**语法**：`docker inspect [OPTIONS] NAME|ID [NAME|ID...]`

**常用选项**：
- `-f, --format`：使用Go模板格式化输出
- `-s, --size`：显示容器的总文件大小
- `--type`：返回指定类型的JSON

**示例**：
```bash
docker inspect my-container
docker inspect -f '{{.NetworkSettings.IPAddress}}' my-container
docker inspect -f '{{json .Config}}' my-container
```

### docker cp

**说明**：在容器和本地文件系统之间复制文件/文件夹

**语法**：
- `docker cp [OPTIONS] CONTAINER:SRC_PATH DEST_PATH`
- `docker cp [OPTIONS] SRC_PATH CONTAINER:DEST_PATH`

**常用选项**：
- `-a, --archive`：归档模式（复制所有uid/gid信息）
- `-L, --follow-link`：始终跟随SRC_PATH中的符号链接

**示例**：
```bash
docker cp my-container:/app/config.json ./
docker cp ./local-file.txt my-container:/app/
docker cp -a ./local-dir/ my-container:/app/
```

### docker commit

**说明**：从容器创建一个新镜像

**语法**：`docker commit [OPTIONS] CONTAINER [REPOSITORY[:TAG]]`

**常用选项**：
- `-a, --author`：作者信息
- `-c, --change`：将Dockerfile指令应用于创建的镜像
- `-m, --message`：提交信息
- `-p, --pause`：在提交期间暂停容器（默认为true）

**示例**：
```bash
docker commit my-container my-image:tag
docker commit -m "Added new feature" my-container my-image:v2
docker commit -c "CMD [\"nginx\", \"-g\", \"daemon off;\"]" my-container my-nginx:custom
```

### docker stats

**说明**：显示容器资源使用统计信息的实时流

**语法**：`docker stats [OPTIONS] [CONTAINER...]`

**常用选项**：
- `-a, --all`：显示所有容器（默认只显示运行中的）
- `--format`：使用Go模板格式化输出
- `--no-stream`：禁用流统计信息，只拉取第一个结果
- `--no-trunc`：不截断输出

**示例**：
```bash
docker stats
docker stats my-container
docker stats --no-stream
```

### docker top

**说明**：显示容器中运行的进程

**语法**：`docker top CONTAINER [ps OPTIONS]`

**示例**：
```bash
docker top my-container
docker top my-container aux
```

### docker pause/unpause

**说明**：暂停/恢复容器中的所有进程

**语法**：
- `docker pause CONTAINER [CONTAINER...]`
- `docker unpause CONTAINER [CONTAINER...]`

**示例**：
```bash
docker pause my-container
docker unpause my-container
```

### docker attach

**说明**：连接到正在运行的容器

**语法**：`docker attach [OPTIONS] CONTAINER`

**常用选项**：
- `--detach-keys`：覆盖用于分离容器的键序列
- `--no-stdin`：不附加STDIN
- `--sig-proxy`：代理所有接收到的信号到进程（默认为true）

**示例**：
```bash
docker attach my-container
docker attach --detach-keys="ctrl-c" my-container
```

### docker rename

**说明**：重命名容器

**语法**：`docker rename CONTAINER NEW_NAME`

**示例**：
```bash
docker rename old-name new-name
```

### docker update

**说明**：更新一个或多个容器的配置

**语法**：`docker update [OPTIONS] CONTAINER [CONTAINER...]`

**常用选项**：
- `--cpus`：CPU配额
- `--memory, -m`：内存限制
- `--memory-swap`：交换限制等于内存加上交换
- `--restart`：重启策略

**示例**：
```bash
docker update --cpus 2 my-container
docker update --memory 512m my-container
docker update --restart=always my-container
```

### docker wait

**说明**：阻塞直到一个或多个容器停止，然后打印退出代码

**语法**：`docker wait CONTAINER [CONTAINER...]`

**示例**：
```bash
docker wait my-container
```

### docker port

**说明**：列出容器的端口映射

**语法**：`docker port CONTAINER [PRIVATE_PORT[/PROTO]]`

**示例**：
```bash
docker port my-container
docker port my-container 80
```

## 网络管理

### docker network ls

**说明**：列出网络

**语法**：`docker network ls [OPTIONS]`

**常用选项**：
- `-f, --filter`：根据条件过滤输出
- `--no-trunc`：不截断输出
- `-q, --quiet`：只显示网络ID

**示例**：
```bash
docker network ls
docker network ls -q
docker network ls --filter driver=bridge
```

### docker network create

**说明**：创建一个网络

**语法**：`docker network create [OPTIONS] NETWORK`

**常用选项**：
- `--driver, -d`：网络驱动（默认为"bridge"）
- `--gateway`：主子网的IPv4或IPv6网关
- `--ip-range`：从子范围分配容器IP的范围
- `--subnet`：子网CIDR格式的网段
- `--ipv6`：启用IPv6网络

**示例**：
```bash
docker network create my-network
docker network create --driver bridge my-network
docker network create --subnet=192.168.0.0/16 --ip-range=192.168.5.0/24 my-network
```

### docker network connect/disconnect

**说明**：将容器连接到/断开与网络的连接

**语法**：
- `docker network connect [OPTIONS] NETWORK CONTAINER`
- `docker network disconnect [OPTIONS] NETWORK CONTAINER`

**常用选项**：
- `--alias`：为容器添加网络范围的别名（仅适用于connect）
- `--ip`：指定IP地址（仅适用于connect）
- `--ip6`：指定IPv6地址（仅适用于connect）
- `-f, --force`：强制容器断开连接（仅适用于disconnect）

**示例**：
```bash
docker network connect my-network my-container
docker network connect --ip 192.168.1.10 my-network my-container
docker network disconnect my-network my-container
```

### docker network rm

**说明**：删除一个或多个网络

**语法**：`docker network rm NETWORK [NETWORK...]`

**示例**：
```bash
docker network rm my-network
docker network rm $(docker network ls -q)
```

### docker network inspect

**说明**：显示一个或多个网络的详细信息

**语法**：`docker network inspect [OPTIONS] NETWORK [NETWORK...]`

**常用选项**：
- `-f, --format`：使用Go模板格式化输出
- `-v, --verbose`：详细输出

**示例**：
```bash
docker network inspect my-network
docker network inspect -f '{{.IPAM.Config}}' my-network
```

### docker network prune

**说明**：删除所有未使用的网络

**语法**：`docker network prune [OPTIONS]`

**常用选项**：
- `-f, --force`：不提示确认
- `--filter`：根据条件过滤

**示例**：
```bash
docker network prune
docker network prune -f
```

## 数据卷管理

### docker volume ls

**说明**：列出卷

**语法**：`docker volume ls [OPTIONS]`

**常用选项**：
- `-f, --filter`：根据条件过滤输出
- `-q, --quiet`：只显示卷名

**示例**：
```bash
docker volume ls
docker volume ls -q
docker volume ls --filter dangling=true
```

### docker volume create

**说明**：创建一个卷

**语法**：`docker volume create [OPTIONS] [VOLUME]`

**常用选项**：
- `--driver, -d`：卷驱动名称（默认为"local"）
- `--label`：设置卷的元数据
- `--name`：指定卷名
- `--opt, -o`：设置驱动特定选项

**示例**：
```bash
docker volume create my-volume
docker volume create --driver local --opt type=nfs --opt o=addr=192.168.1.1,rw --opt device=:/path/to/dir my-nfs-volume
```

### docker volume rm

**说明**：删除一个或多个卷

**语法**：`docker volume rm [OPTIONS] VOLUME [VOLUME...]`

**常用选项**：
- `-f, --force`：强制删除卷

**示例**：
```bash
docker volume rm my-volume
docker volume rm $(docker volume ls -q)
```

### docker volume inspect

**说明**：显示一个或多个卷的详细信息

**语法**：`docker volume inspect [OPTIONS] VOLUME [VOLUME...]`

**常用选项**：
- `-f, --format`：使用Go模板格式化输出

**示例**：
```bash
docker volume inspect my-volume
docker volume inspect -f '{{.Mountpoint}}' my-volume
```

### docker volume prune

**说明**：删除所有未使用的本地卷

**语法**：`docker volume prune [OPTIONS]`

**常用选项**：
- `-f, --force`：不提示确认
- `--filter`：根据条件过滤

**示例**：
```bash
docker volume prune
docker volume prune -f
```
docker volume inspect my-volume
docker volume inspect -f '{{.Mountpoint}}' my-volume
```

### docker volume prune

**说明**：删除所有未使用的本地卷

**语法**：`docker volume prune [OPTIONS]`

**常用选项**：
- `-f, --force`：不提示确认
- `--filter`：根据条件过滤

**示例**：
```bash
docker volume prune
docker volume prune -f
```

## Docker Compose

### docker-compose up

**说明**：创建并启动容器

**语法**：`docker-compose up [OPTIONS] [SERVICE...]`

**常用选项**：
- `-d, --detach`：后台运行容器
- `--build`：启动容器前构建镜像
- `--no-build`：不构建镜像，即使镜像不存在
- `--force-recreate`：强制重新创建容器
- `--no-recreate`：如果容器已存在，则不重新创建
- `--no-start`：创建但不启动容器
- `--scale`：设置服务的容器数量

**示例**：
```bash
docker-compose up
docker-compose up -d
docker-compose up --build
docker-compose up -d --scale web=3 --scale db=1
```

### docker-compose down

**说明**：停止并删除容器、网络

**语法**：`docker-compose down [OPTIONS]`

**常用选项**：
- `--rmi`：删除镜像，类型：all（所有镜像）或local（仅本地构建的镜像）
- `-v, --volumes`：删除声明的卷和附加到容器的匿名卷
- `--remove-orphans`：删除未在配置中定义的服务的容器

**示例**：
```bash
docker-compose down
docker-compose down --rmi all -v
```

### docker-compose ps

**说明**：列出容器

**语法**：`docker-compose ps [OPTIONS] [SERVICE...]`

**常用选项**：
- `-q, --quiet`：只显示ID
- `--services`：显示服务
- `--filter`：根据条件过滤服务

**示例**：
```bash
docker-compose ps
docker-compose ps -q
```

### docker-compose logs

**说明**：查看服务的输出

**语法**：`docker-compose logs [OPTIONS] [SERVICE...]`

**常用选项**：
- `-f, --follow`：跟踪日志输出
- `--tail`：从日志末尾显示的行数
- `-t, --timestamps`：显示时间戳

**示例**：
```bash
docker-compose logs
docker-compose logs -f web
docker-compose logs --tail=100 db
```

### docker-compose build

**说明**：构建或重建服务

**语法**：`docker-compose build [OPTIONS] [SERVICE...]`

**常用选项**：
- `--no-cache`：构建镜像时不使用缓存
- `--pull`：始终尝试拉取更新的镜像
- `--parallel`：并行构建镜像

**示例**：
```bash
docker-compose build
docker-compose build --no-cache
docker-compose build web
```

### docker-compose exec

**说明**：在运行的容器中执行命令

**语法**：`docker-compose exec [OPTIONS] SERVICE COMMAND [ARGS...]`

**常用选项**：
- `-d, --detach`：后台运行命令
- `--index`：如果有多个实例，指定容器索引
- `-T`：禁用伪TTY分配
- `--user, -u`：指定用户

**示例**：
```bash
docker-compose exec web bash
docker-compose exec -T db mysql -u root -p
```

### docker-compose run

**说明**：在服务上运行一次性命令

**语法**：`docker-compose run [OPTIONS] SERVICE [COMMAND] [ARGS...]`

**常用选项**：
- `-d, --detach`：后台运行容器
- `--name`：为容器指定名称
- `--no-deps`：不启动链接的服务
- `--rm`：命令完成后删除容器
- `-T`：禁用伪TTY分配

**示例**：
```bash
docker-compose run web bash
docker-compose run --rm web npm test
```

### docker-compose config

**说明**：验证并查看Compose文件

**语法**：`docker-compose config [OPTIONS]`

**常用选项**：
- `--services`：打印服务名称
- `--volumes`：打印卷名称
- `--resolve-image-digests`：将镜像标签解析为摘要

**示例**：
```bash
docker-compose config
docker-compose config --services
```

### docker-compose pull

**说明**：拉取服务镜像

**语法**：`docker-compose pull [OPTIONS] [SERVICE...]`

**常用选项**：
- `--ignore-pull-failures`：忽略拉取失败
- `--parallel`：并行拉取
- `--quiet`：不打印进度信息

**示例**：
```bash
docker-compose pull
docker-compose pull web db
```

### docker-compose restart

**说明**：重启服务

**语法**：`docker-compose restart [OPTIONS] [SERVICE...]`

**常用选项**：
- `-t, --timeout`：指定关闭超时（秒）

**示例**：
```bash
docker-compose restart
docker-compose restart -t 30 web
```

### docker-compose stop

**说明**：停止服务

**语法**：`docker-compose stop [OPTIONS] [SERVICE...]`

**常用选项**：
- `-t, --timeout`：指定关闭超时（秒）

**示例**：
```bash
docker-compose stop
docker-compose stop web
```

## 常见问题FAQ

### 1. 如何解决 Docker 容器无法访问外网的问题？

**问题描述**：Docker 容器启动后无法访问外部网络。

**解决方案**：
1. 检查宿主机的网络连接是否正常
2. 检查 Docker 的网络设置：
   ```bash
   docker network ls
   docker network inspect bridge
   ```
3. 确保 Docker 的 DNS 设置正确，可以在 `/etc/docker/daemon.json` 中添加：
   ```json
   {
     "dns": ["8.8.8.8", "8.8.4.4"]
   }
   ```
4. 重启 Docker 服务：
   ```bash
   systemctl restart docker
   ```

### 2. 如何解决 Docker 镜像拉取速度慢的问题？

**问题描述**：从 Docker Hub 拉取镜像时速度非常慢。

**解决方案**：
1. 使用国内镜像源，在 `/etc/docker/daemon.json` 中添加：
   ```json
   {
     "registry-mirrors": [
       "https://registry.docker-cn.com",
       "https://docker.mirrors.ustc.edu.cn",
       "https://hub-mirror.c.163.com"
     ]
   }
   ```
2. 重启 Docker 服务：
   ```bash
   systemctl restart docker
   ```

### 3. 如何解决 Docker 容器日志占用空间过大的问题？

**问题描述**：Docker 容器运行一段时间后，日志文件占用大量磁盘空间。

**解决方案**：
1. 配置日志轮转，在 `/etc/docker/daemon.json` 中添加：
   ```json
   {
     "log-driver": "json-file",
     "log-opts": {
       "max-size": "10m",
       "max-file": "3"
     }
   }
   ```
2. 重启 Docker 服务：
   ```bash
   systemctl restart docker
   ```
3. 对于已存在的容器，可以使用以下命令清理日志：
   ```bash
   truncate -s 0 $(docker inspect --format='{{.LogPath}}' CONTAINER_ID)
   ```

### 4. 如何解决 Docker 容器无法自动启动的问题？

**问题描述**：系统重启后，Docker 容器没有自动启动。

**解决方案**：
1. 使用 `--restart` 参数设置容器的重启策略：
   ```bash
   docker update --restart=always CONTAINER_ID
   ```
2. 常用的重启策略有：
   - `no`：不自动重启（默认）
   - `on-failure[:max-retries]`：容器退出且返回值非0时重启
   - `always`：总是重启
   - `unless-stopped`：总是重启，除非手动停止

### 5. 如何解决 Docker 容器内无法访问宿主机的问题？

**问题描述**：Docker 容器内无法通过 localhost 或 127.0.0.1 访问宿主机服务。

**解决方案**：
1. 在 Linux 上，可以使用特殊的 DNS 名称 `host.docker.internal` 访问宿主机：
   ```bash
   # 在容器内执行
   ping host.docker.internal
   ```
2. 如果上述方法不可用，可以使用宿主机的实际 IP 地址
3. 使用 `--network=host` 参数启动容器，使容器共享宿主机的网络命名空间（注意：这会失去网络隔离性）

### 6. 如何解决 Docker 磁盘空间不足的问题？

**问题描述**：Docker 使用一段时间后，占用大量磁盘空间。

**解决方案**：
1. 清理未使用的镜像、容器和卷：
   ```bash
   docker system prune -a --volumes
   ```
2. 单独清理各类资源：
   ```bash
   # 清理悬空镜像
   docker image prune
   
   # 清理停止的容器
   docker container prune
   
   # 清理未使用的卷
   docker volume prune
   
   # 清理未使用的网络
   docker network prune
   ```
3. 定期执行清理任务，可以设置 cron 任务

### 7. 如何解决 Docker 容器之间通信问题？

**问题描述**：多个 Docker 容器之间无法相互访问。

**解决方案**：
1. 创建自定义网络，并将容器连接到该网络：
   ```bash
   docker network create my-network
   docker run --network=my-network --name container1 -d nginx
   docker run --network=my-network --name container2 -d nginx
   ```
2. 在同一网络中的容器可以通过容器名称相互访问：
   ```bash
   # 在container2中访问container1
   curl container1
   ```
3. 使用Docker Compose管理多容器应用，它会自动创建一个网络并连接所有服务

### 8. 如何在Windows或Mac上使用Docker的Linux容器？

**问题描述**：在Windows或Mac上需要运行Linux容器。

**解决方案**：
1. 安装Docker Desktop，它包含了运行Linux容器所需的所有组件
2. 在Windows上，确保已启用WSL 2（Windows Subsystem for Linux 2）
3. 在Docker Desktop设置中，确保选择了"Use the WSL 2 based engine"（Windows）或确认Virtualization框架已启用（Mac）
4. 启动Docker Desktop后，可以通过命令行或Docker Desktop界面使用Docker命令
5. 所有Docker命令与Linux上的使用方式相同

### 9. 如何解决Docker镜像构建失败的问题？

**问题描述**：使用`docker build`命令构建镜像时失败。

**解决方案**：
1. 检查Dockerfile语法是否正确
2. 确保构建上下文中包含所有必要的文件
3. 查看构建日志，找出具体的错误原因：
   ```bash
   docker build --no-cache -t myimage . 2>&1 | tee build.log
   ```
4. 如果是网络问题，可以尝试使用`--network=host`参数：
   ```bash
   docker build --network=host -t myimage .
   ```
5. 如果是资源问题，可以增加Docker可用资源（内存、CPU等）

### 10. 如何在Docker容器中持久化数据？

**问题描述**：Docker容器重启后数据丢失。

**解决方案**：
1. 使用Docker卷（Volumes）持久化数据：
   ```bash
   docker volume create my-data
   docker run -v my-data:/app/data myimage
   ```
2. 使用绑定挂载（Bind Mounts）将宿主机目录挂载到容器：
   ```bash
   docker run -v /host/path:/container/path myimage
   ```
3. 对于数据库等应用，使用专门的数据卷容器：
   ```bash
   docker run --name db-data -v /var/lib/mysql busybox
   docker run --volumes-from db-data mysql
   ```
4. 使用Docker Compose管理持久化数据：
   ```yaml
   version: '3'
   services:
     web:
       image: nginx
       volumes:
         - web-data:/usr/share/nginx/html
   volumes:
     web-data:
   ```