#!/usr/bin/env bash
# ==============================================================================
# 环境变量加载脚本（Linux / macOS / Git Bash / WSL）
# ------------------------------------------------------------------------------
# 作用：把项目根目录 .env 中的变量导入【当前 shell 会话】，使本机直接运行
#       Spring Boot 时也能读到同一套配置。
#
# 为什么需要这个脚本：
#   Docker Compose 会自动读取 .env 并注入容器；而 Spring Boot【不读取 .env】，
#   只读操作系统环境变量。本机开发时二者之间需要这座"桥"。
#
# 用法（注意开头的 source 或点号，否则变量不会保留在当前会话）：
#   source ./scripts/load-env.sh
#   或
#   . ./scripts/load-env.sh
#
# 之后即可正常启动后端：
#   ./mvnw -pl volunteer-backend/volunteer-server -am spring-boot:run
#
# 可选参数：
#   --file <路径>   指定其他 env 文件，默认 <项目根>/.env
#   --local         本机模式：自动把容器内主机名改写为 localhost
#   --quiet         只加载，不打印明细
# ==============================================================================

set -o pipefail

# ---- 默认参数 ----
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ENV_FILE="${PROJECT_ROOT}/.env"
LOCAL_MODE=0
QUIET=0

# ---- 解析参数 ----
while [ $# -gt 0 ]; do
  case "$1" in
    --file)  ENV_FILE="$2"; shift 2 ;;
    --local) LOCAL_MODE=1; shift ;;
    --quiet) QUIET=1; shift ;;
    -h|--help)
      sed -n '2,30p' "$0"
      return 0 2>/dev/null || exit 0
      ;;
    *) echo "[!] 未知参数：$1" >&2; shift ;;
  esac
done

if [ ! -f "$ENV_FILE" ]; then
  echo "[X] 未找到环境变量文件：$ENV_FILE" >&2
  echo "    请先创建：cp .env.example .env" >&2
  return 1 2>/dev/null || exit 1
fi

loaded=0
declare -a local_overrides=()

# ---- 逐行解析（IFS='=' 只切第一个等号，兼容值中含 = 的连接串）----
while IFS= read -r line || [ -n "$line" ]; do
  # 去掉首尾空白与 Windows 换行
  line="$(printf '%s' "$line" | tr -d '\r' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"

  # 跳过空行与注释
  case "$line" in
    ''|\#*) continue ;;
  esac

  case "$line" in
    *=*) ;;
    *) continue ;;
  esac

  key="${line%%=*}"
  value="${line#*=}"

  # 去掉键的空格
  key="$(printf '%s' "$key" | tr -d '[:space:]')"
  [ -z "$key" ] && continue

  # 去掉成对引号
  case "$value" in
    \"*\") value="${value#\"}"; value="${value%\"}" ;;
    \'*\') value="${value#\'}"; value="${value%\'}" ;;
  esac

  # 本机模式：把容器内主机名改写为 localhost
  if [ "$LOCAL_MODE" -eq 1 ]; then
    case "$key" in
      SPRING_DATASOURCE_URL)
        before="$value"
        value="$(printf '%s' "$value" | sed -e 's#//mysql:#//localhost:#' -e 's#//host\.docker\.internal:#//localhost:#')"
        [ "$value" != "$before" ] && local_overrides+=("SPRING_DATASOURCE_URL -> localhost")
        ;;
      REDIS_HOST)
        if [ "$value" = "redis" ]; then value="localhost"; local_overrides+=("REDIS_HOST -> localhost"); fi
        ;;
      MINIO_ENDPOINT)
        before="$value"
        value="$(printf '%s' "$value" | sed 's#//minio:9000#//localhost:9090#')"
        [ "$value" != "$before" ] && local_overrides+=("MINIO_ENDPOINT -> http://localhost:9090")
        ;;
    esac
  fi

  export "$key=$value"
  loaded=$((loaded + 1))

  if [ "$QUIET" -eq 0 ]; then
    case "$key" in
      *PASSWORD*|*SECRET*|*API_KEY*|*ACCESS_KEY*)
        printf '  %-48s = <已设置，值不外显>\n' "$key"
        ;;
      *)
        printf '  %-48s = %s\n' "$key" "$value"
        ;;
    esac
  fi
done < "$ENV_FILE"

echo ""
echo "[OK] 已从 $ENV_FILE 加载 $loaded 个环境变量到当前会话"

if [ "${#local_overrides[@]}" -gt 0 ]; then
  echo "[本机模式] 已应用以下地址改写："
  for item in "${local_overrides[@]}"; do echo "  - $item"; done
fi

if [ "$QUIET" -eq 0 ]; then
  echo ""
  echo "提示：变量仅在【当前 shell 会话】有效，关闭终端即失效。"
  echo "     新开终端需重新执行：source ./scripts/load-env.sh"
fi
