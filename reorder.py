import re

with open('app/src/main/java/com/example/financemanager/ui/screens/QuickEntryScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Change Category condition
content = content.replace(
    'if (selectedType != TransactionType.TRANSFER) {',
    'if (selectedType == TransactionType.EXPENSE) {'
)

# 2. Reorder blocks.
# Block 5: NLP (starts at '// 5. NLP Input field' and ends before '// 5.5 Date Picker')
nlp_pattern = re.compile(r'( {16}// 5\. NLP Input field & OCR button.*?)( {16}// 5\.5 Date Picker)', re.DOTALL)
nlp_match = nlp_pattern.search(content)
if nlp_match:
    nlp_block = nlp_match.group(1)
    
    # Remove NLP block from its original position
    content = content[:nlp_match.start(1)] + content[nlp_match.end(1):]
    
    # Now insert NLP block after Note (and before Recurring)
    recurring_pattern = re.compile(r'( {16}// 7\. Recurring Option Toggle)')
    recurring_match = recurring_pattern.search(content)
    
    if recurring_match:
        content = content[:recurring_match.start(1)] + nlp_block + content[recurring_match.start(1):]

# 3. Enhance NLP block
new_nlp = """                // 5. NLP Input field & OCR button (Moved down)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Smart Entry (Optional)", style = Typography.labelMedium.copy(color = TextSecondary))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
"""

content = content.replace(
    """                // 5. NLP Input field & OCR button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
""",
    new_nlp
)

with open('app/src/main/java/com/example/financemanager/ui/screens/QuickEntryScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print('Success')
