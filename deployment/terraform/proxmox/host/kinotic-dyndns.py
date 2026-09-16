#!/opt/certbot/bin/python3
"""Keeps the host's public hostnames on the router's current public address. The ISP hands
out the address by DHCP, so kinotic-dyndns.timer runs this every few minutes: it asks an echo
service for the address and writes it into the A record of every name the host's certificate
carries, in the zone certbot's dns-azure configuration names, as the same principal.

A record already at the address is left alone; nothing else in the zone is touched.
"""
import configparser
import glob
import sys
import urllib.request

from azure.identity import ClientSecretCredential
from azure.mgmt.dns import DnsManagementClient
from cryptography import x509

CERTBOT_INI = "/etc/kinotic/certbot-azure.ini"
LINEAGES = "/etc/letsencrypt/live/*/cert.pem"
ECHO = "https://api.ipify.org"


def settings():
    parser = configparser.ConfigParser()
    with open(CERTBOT_INI) as f:
        parser.read_string("[certbot]\n" + f.read())
    ini = parser["certbot"]
    zone, _, scope = ini["dns_azure_zone1"].partition(":")
    subscription = scope.split("/")[2]
    group = scope.rsplit("/", 1)[1]
    credential = ClientSecretCredential(ini["dns_azure_tenant_id"], ini["dns_azure_sp_client_id"],
                                        ini["dns_azure_sp_client_secret"])
    return zone.strip(), subscription, group, credential


def hostnames():
    names = set()
    for path in glob.glob(LINEAGES):
        with open(path, "rb") as f:
            cert = x509.load_pem_x509_certificate(f.read())
        names.update(cert.extensions.get_extension_for_class(x509.SubjectAlternativeName).value.get_values_for_type(x509.DNSName))
    return sorted(names)


def public_address():
    with urllib.request.urlopen(ECHO, timeout=15) as response:
        return response.read().decode().strip()


if __name__ == "__main__":
    zone, subscription, group, credential = settings()
    names = [n for n in hostnames() if n.endswith("." + zone)]
    if not names:
        sys.exit("no certificate for a name in " + zone)
    address = public_address()
    dns = DnsManagementClient(credential, subscription)
    for name in names:
        label = name[: -len(zone) - 1]
        try:
            current = [r.ipv4_address for r in dns.record_sets.get(group, zone, label, "A").a_records]
        except Exception:
            current = []
        if current == [address]:
            continue
        dns.record_sets.create_or_update(group, zone, label, "A", {"ttl": 300, "a_records": [{"ipv4_address": address}]})
        print(f"{name}: {current or 'unset'} -> {address}")
