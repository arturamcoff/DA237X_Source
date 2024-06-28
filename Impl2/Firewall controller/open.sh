#!/bin/bash
iptables -A INPUT -s <SOURCE-IP> -d <IP OF ON-PREM PROXY> -p TCP -j ACCEPT
echo "Hello Open!"

