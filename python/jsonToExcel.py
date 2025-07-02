import pandas as pd
import json

def json_to_excel(json_file, excel_file='output.xlsx'):
    """
    将JSON文件转换为Excel表格

    Args:
        json_file (str): JSON文件路径
        excel_file (str): 输出Excel文件路径（默认output.xlsx）
    """
    try:
        # 读取JSON文件
        with open(json_file, 'r', encoding='utf-8') as f:
            data = json.load(f)

        # 将JSON数组转换为DataFrame
        df = pd.DataFrame(data)

        # 保存为Excel文件
        df.to_excel(excel_file, index=False)
        print(f"转换成功！Excel文件已保存至：{excel_file}")

    except FileNotFoundError:
        print(f"错误：文件 {json_file} 未找到")
    except json.JSONDecodeError:
        print("错误：JSON格式解析失败")
    except Exception as e:
        print(f"转换过程中发生错误：{str(e)}")

# 示例用法
if __name__ == "__main__":
    # 替换为你的JSON文件路径
    input_json = "data.json"
    # 可选：指定输出文件名
    output_excel = "result.xlsx"

    json_to_excel(input_json, output_excel)
