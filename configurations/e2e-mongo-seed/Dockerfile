FROM mongo

# This Dockerfile does not run e2e tests. It instead creates an otp-middleware instance with pre-filled data which can be used in e2e tests.

COPY api-e2euser.json /api-e2euser.json
COPY admin-e2euser.json /admin-e2euser.json
CMD mongoimport --uri="mongodb://mongo:27017/otp_middleware" --collection=AdminUser /admin-e2euser.json && mongoimport --uri="mongodb://mongo:27017/otp_middleware" --collection=ApiUser /api-e2euser.json