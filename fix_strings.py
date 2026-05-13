import os
import re

def fix_content(content):
    pattern = re.compile(r'(<string name="[^"]+">)(.*?)(</string>)', re.DOTALL)
    
    def replace_func(match):
        start_tag = match.group(1)
        text = match.group(2)
        end_tag = match.group(3)
        
        if 'formatted="false"' in start_tag:
            return match.group(0)
            
        # البحث عن %s أو %d أو %1$s إلخ
        placeholders = re.findall(r'%[0-9]*\$?[sd]', text)
        if len(placeholders) > 1:
            # إذا كان هناك أكثر من متغير وأحدها على الأقل لا يحتوي على $
            if any('$' not in p for p in placeholders):
                count = 1
                # استبدال فقط %s و %d التي لا تتبعها $
                def sub_placeholder(m):
                    nonlocal count
                    p = m.group(0)
                    # إذا كان التنسيق يحتوي بالفعل على $ لا نغيره
                    if '$' in p:
                        return p
                    new_p = f'%{count}${p[1:]}'
                    count += 1
                    return new_p
                
                # استبدال %s أو %d بشرط ألا يكون جزءاً من %1$s
                # سنستخدم حيلة بسيطة: استبدال كل المتغيرات ثم إعادة بناء الترتيب
                all_placeholders = re.findall(r'%[0-9]*\$?[sd]', text)
                new_text = text
                for i, p in enumerate(all_placeholders):
                    if '$' not in p:
                        target = p
                        replacement = f'%{i+1}${p[1:]}'
                        new_text = new_text.replace(target, replacement, 1)
                
                return f'{start_tag}{new_text}{end_tag}'
        
        return match.group(0)

    return pattern.sub(replace_func, content)

for root, dirs, files in os.walk('/home/ubuntu/Rvpn/app/src/main/res'):
    for file in files:
        if file == 'strings.xml':
            path = os.path.join(root, file)
            with open(path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            new_content = fix_content(content)
            
            if new_content != content:
                with open(path, 'w', encoding='utf-8') as f:
                    f.write(new_content)
                print(f"Fixed: {path}")
