# 一带一路合作态势分析系统 —— 常用命令
#
# 用法：
#   make            等价于 make help
#   make demo       生成示例数据库（如不存在）并启动程序
#   make run        直接启动程序（数据库需已存在）
#   make demo-db    从 data/sample/ 导入示例数据，生成 bri.db
#   make package    打包成可执行 fat jar
#   make clean      清理构建产物

MVN ?= mvn
JAR  = target/bri-analysis-1.0.0.jar
DB   = bri.db

.PHONY: help run demo demo-db package test clean

help:
	@echo "常用命令："
	@echo "  make demo       生成示例数据库并启动程序（推荐首次运行）"
	@echo "  make run        启动程序"
	@echo "  make demo-db    仅生成示例数据库 bri.db"
	@echo "  make package    打包 fat jar → $(JAR)"
	@echo "  make test       编译并运行烟雾测试"
	@echo "  make clean      清理构建产物"

run:
	$(MVN) -q javafx:run

demo: $(DB)
	$(MVN) -q javafx:run

demo-db: $(DB)

# 数据库不存在时才导入，避免覆盖你已有的数据
$(DB):
	$(MVN) -q -DskipTests compile
	$(MVN) -q exec:java -Dexec.mainClass=com.bri.BulkImport -Dexec.args="data/sample $(DB)"

package:
	$(MVN) clean package

test:
	$(MVN) -q test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.bri.SmokeTest

clean:
	$(MVN) clean
	rm -f $(DB) $(DB)-shm $(DB)-wal
