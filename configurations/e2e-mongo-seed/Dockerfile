FROM mongo

COPY api-e2euser.json /api-e2euser.json
COPY admin-e2euser.json /admin-e2euser.json
CMD mongoimport --uri="mongodb://mongo:27017/otp_middleware" --collection=AdminUser /admin-e2euser.json && mongoimport --uri="mongodb://mongo:27017/otp_middleware" --collection=ApiUser /api-e2euser.json