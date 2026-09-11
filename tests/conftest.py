"""pytest 全局夹具。

**这是一套真正的接口自动化测试**：通过 HTTP 打真实运行的后端、连真实数据库，
而不是进程内的 MockMvc。因此它验证的是端到端行为——包括序列化、过滤器链、
事务提交——这些是单元测试覆盖不到的。

代价是**写入会真实落库、不会回滚**。所以本套用例遵循两条纪律：

1. 断言尽量用**差值**（操作前后的变化量），而不是"总数等于 21"这类绝对值。
   这样即使库里已经积累了很多测试数据，用例依然稳定。
2. 需要写数据时，一律用**当次运行新建的用户**，不去改动种子账号。

跑完一轮后数据库会多出一些测试用户，需要干净环境时重跑 `schema.sql` + `data.sql`。
"""

from __future__ import annotations

import os
import uuid

import pytest
import requests

from utils.client import ApiClient

DEFAULT_BASE_URL = "http://localhost:8081"

# 种子账号（见 database/data.sql），密码统一是 123456
SEED_USER = "test001"          # id=2，主测试账号
SEED_USER_2 = "test002"        # id=3，与 test001 互相关注
SEED_ADMIN = "admin"           # id=1，管理员
SEED_BANNED = "banned001"      # id=12，已禁用
SEED_DELETED = "deleted001"    # id=13，已注销


def pytest_addoption(parser: pytest.Parser) -> None:
    parser.addoption(
        "--base-url",
        action="store",
        default=None,
        help=f"被测后端地址，默认 {DEFAULT_BASE_URL}（也可用环境变量 MIQU_BASE_URL）",
    )


@pytest.fixture(scope="session")
def base_url(pytestconfig: pytest.Config) -> str:
    return (
        pytestconfig.getoption("--base-url")
        or os.getenv("MIQU_BASE_URL")
        or DEFAULT_BASE_URL
    ).rstrip("/")


@pytest.fixture(scope="session", autouse=True)
def ensure_backend_running(base_url: str) -> None:
    """开跑前先探测后端。

    没有这道检查时，后端没起来会让所有用例以"连接被拒绝"的形式失败，
    看起来像是接口挂了，实际只是没启动——排查成本很高。
    """
    try:
        response = requests.get(f"{base_url}/api/health", timeout=5)
    except requests.RequestException as exc:
        pytest.exit(
            f"\n后端未启动或不可达：{base_url}"
            f"\n请先启动后端（cd backend && mvn spring-boot:run），"
            f"或用 --base-url / MIQU_BASE_URL 指定地址。"
            f"\n原因：{exc}\n",
            returncode=1,
        )

    payload = response.json()
    if payload.get("code") != 200:
        pytest.exit(f"\n健康检查未通过：{payload}\n", returncode=1)


@pytest.fixture
def anonymous(base_url: str) -> ApiClient:
    """未登录的客户端。

    **必须是函数级作用域。** `ApiClient.login()` 会把 Token 记到实例上，
    如果这个 fixture 是 session 级的，那么第一次有人调用
    `anonymous.login(...)`（例如 test_login_success）之后，
    后面所有拿到 `anonymous` 的用例都变成"已登录"了。

    后果不是报错那么简单：`test_delete_post_requires_login` 会**真的把 post 1 删掉**，
    然后一连串看似无关的用例开始莫名其妙地失败，排查方向完全跑偏。
    这里踩过一次，所以显式写成函数级。
    """
    return ApiClient(base_url)


@pytest.fixture(scope="session")
def seed_user_client(base_url: str) -> ApiClient:
    """已登录的种子用户 test001。"""
    client = ApiClient(base_url)
    response = client.login(SEED_USER)
    if not response.ok:
        pytest.exit(f"种子账号 {SEED_USER} 登录失败：{response.message}", returncode=1)
    return client


@pytest.fixture(scope="session")
def seed_user_2_client(base_url: str) -> ApiClient:
    """已登录的种子用户 test002（用于被测对象不是自己的场景）。"""
    client = ApiClient(base_url)
    client.login(SEED_USER_2)
    return client


@pytest.fixture(scope="session")
def admin_client(base_url: str) -> ApiClient:
    """已登录的管理员。"""
    client = ApiClient(base_url)
    response = client.login(SEED_ADMIN)
    if not response.ok:
        pytest.exit(f"管理员登录失败：{response.message}", returncode=1)
    return client


@pytest.fixture
def fresh_user(base_url: str):
    """注册一个全新用户并返回已登录的客户端。

    用户名带随机后缀：测试会真实落库且无法通过接口删除用户，
    固定用户名第二次运行就会撞唯一键。
    """

    def _create(prefix: str = "qa") -> ApiClient:
        username = f"{prefix}{uuid.uuid4().hex[:10]}"
        client = ApiClient(base_url)
        response = client.register(username)
        assert response.ok, f"注册测试用户失败：{response.code} {response.message}"
        login = client.login(username)
        assert login.ok, f"测试用户登录失败：{login.message}"
        return client

    return _create


@pytest.fixture
def fresh_users(base_url: str):
    """一次创建多个互不相同的用户。"""

    def _create(count: int = 2, prefix: str = "qa") -> list[ApiClient]:
        clients = []
        for _ in range(count):
            username = f"{prefix}{uuid.uuid4().hex[:10]}"
            client = ApiClient(base_url)
            response = client.register(username)
            assert response.ok, f"注册测试用户失败：{response.code} {response.message}"
            login = client.login(username)
            assert login.ok, f"测试用户登录失败：{login.message}"
            clients.append(client)
        return clients

    return _create


# ---------- 通用小工具 ----------


@pytest.fixture
def unique_suffix() -> str:
    return uuid.uuid4().hex[:10]


def pytest_configure(config: pytest.Config) -> None:
    config.addinivalue_line("markers", "smoke: 冒烟用例")
