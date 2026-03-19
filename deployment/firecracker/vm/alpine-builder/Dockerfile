# Dockerfile — firecracker/vms/firecracker-alpine/Dockerfile
FROM alpine:3.22.2

# set the root password.
RUN echo 'root:root' | chpasswd

# # disable the virtual login terminals.
# TODO: Do we need this see https://github.com/rgl/firecracker-playground/blob/cd751987f9c333a05e37e27a93741e5074bcce16/provision-firecracker-vm-alpine.sh#L29
# RUN sed -i -E 's/^(tty\d+:.+)/#\1/g' /etc/inittab

# install the required packages.
RUN apk add openrc \
            util-linux

# Setup tty and init 
RUN ln -s /etc/init.d/agetty /etc/init.d/agetty.ttyS0 \
	&& echo ttyS0 > /etc/securetty \
	&& rc-update add agetty.ttyS0 default \
	&& rc-update add devfs boot \
	&& rc-update add procfs boot \
	&& rc-update add sysfs boot \
	&& rc-update add local default 

COPY scripts/mount-data.sh /etc/init.d/mount-data
RUN chmod +x /etc/init.d/mount-data \
	&& rc-update add mount-data default
COPY conf/resolv.conf /etc/resolv.conf
COPY scripts/overlay-init.sh /sbin/overlay-init.sh

# Everything below not used by the final VM image
COPY scripts/make-rootfs.sh /work/make-rootfs.sh

CMD ["/work/make-rootfs.sh"]
