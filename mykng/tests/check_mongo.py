from pymongo import MongoClient
client = MongoClient('mongodb://kb:kb123456@localhost:27017/kb_knowledge?authSource=admin')
db = client['kb_knowledge']
print('Collections:', db.list_collection_names())
coll = db['doc_content']
print('doc_content count:', coll.count_documents({}))
sample = coll.find_one()
print('Sample:', sample)
if sample:
    print('Sample keys:', list(sample.keys()))
# Try different field name patterns
for fn in ['docId', 'doc_id', 'docID']:
    c = coll.count_documents({fn: 39})
    print(f'{fn}=39: {c} docs')
# Get all and show
for doc in coll.find().limit(3):
    print('Record:', doc)
