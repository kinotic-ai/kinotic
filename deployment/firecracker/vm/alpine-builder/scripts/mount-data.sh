#!/sbin/openrc-run

description="Mount data disk from Firecracker"

get_cmdline_param() {
    local key="$1" param
    for param in $(cat /proc/cmdline); do
        case "$param" in
            "$key="*) echo "${param#*=}"; return 0 ;;
        esac
    done
    return 0
}

start() {
    local data_root data_mount
    data_root="$(get_cmdline_param data_root)"
    data_mount="$(get_cmdline_param data_mount)"

    [ -z "$data_root" ] && return 0
    [ -z "$data_mount" ] && data_mount="/data"

    ebegin "Mounting data disk"
    if [ ! -b "/dev/$data_root" ]; then
        eend 0 "data device /dev/$data_root not found; skipping"
        return 0
    fi

    mkdir -p "$data_mount"
    if mount -t ext4 "/dev/$data_root" "$data_mount"; then
        eend 0
    else
        eend 0 "failed to mount /dev/$data_root at $data_mount"
    fi
}

depend() {
    need localmount
}
