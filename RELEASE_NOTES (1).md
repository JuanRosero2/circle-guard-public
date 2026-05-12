# Release Notes for v37
Date: Mon May 11 23:30:00 UTC 2026
## Commits in this Release:
4ea8105 chore: sincronizar resiliencia y logs hacia todos los pipelines
6dd652a fix: test automation and Jenkinsfile release notes
1d9c4e7 fix: test automation and Jenkinsfile release notes
4c1fb73 add: test al jenkinsfile3
35329df add: test al jenkinsfile2
f6c73c8 add: test al jenkinsfile
eb4c805 add: test al jenkinsfile
6717140 add: test al jenkinsfile
1c2357f jenkins9
95e1dc8 jenkins9
7fa9217 jenkins9
cd48e25 jenkins9
fe03ae9 jenkins8
581a004 jenkins7
3906314 jenkins6
c6cf307 jenkins5
cf43945 jenkins4
2395d94 jenkins4
9539d7a jenkins3
10172a0 jenkins2
e22821a jenkins
c2cbe8a jenkins28: update credentials to use ghcr-credentials for JuanRosero2
0bf97c0 jenkins27: fix GitHub Container Registry - use lowercase username juanrosero2
cdf80a0 jenkins26: switch to JuanRosero2 account for consistency with main repository
7314f72 jenkins22
674a5fd jenkins25: fix post block indentation - should be at same level as stages
29ce522 jenkins24: fix syntax error - add missing closing brace for post block
d26cde6 jenkins23: fix syntax error - remove extra closing brace on line 271
96b1cd2 jenkins22
f89e128 jenkins22: fix syntax error in post block - correct indentation
bd9c2d7 jenkins21: switch to GitHub Container Registry (ghcr.io) to resolve Docker Hub restrictions
5434530 jenkins20: disable Docker Push temporarily due to Docker Hub account restrictions
130a843 jenkins19: restore circleguard namespace to match actual Docker Hub repository names
b76b9a7 jenkins17: switch to new Docker Hub account juan0073 to resolve Google OAuth issues
cd0fe3f jenkins16: add DOCKER_PUSH_ENABLED flag for conditional Docker push control
71c54dd jenkins15: make docker push optional due to token scope issues - pipeline continues
3f12b7a jenkins14: restore circleguard namespace - repositories already exist in Docker Hub
84eb22a jenkins13: fix docker push - remove circleguard namespace and use direct user namespace
a0087de jenkins12: fix docker push to be optional and continue on failure
ab6330e jenkins11: fix dockerfiles to use Java 17 instead of Java 21 to match project configuration
141d08e jenkins10: fix syntax error - remove nested steps in script block
ad28e2e jenkins9: fix docker build to use sequential builds instead of parallel to avoid resource conflicts
888680c jenkins8: fix gateway-service tests to exclude problematic QrValidationServiceEnhancedTest
e61aaa5 jenkins7
c9e8ea1 jenkins7: add Docker socket mounting comment for new build test
4b33d46 jenkins6
0ff8b75 jenkins5
7f85a11 jenkins4
3df2d91 jenkins3
1bd2bda jenkins
e3fd734 fix: incluir gradle wrapper para Jenkins
46707bd fix: incluir gradle wrapper para Jenkins
34e4779 jenkins
2bc568b pull rama master
