SUMMARY = "Web Platform for Embedded Framework"
HOMEPAGE = "https://github.com/WebPlatformForEmbedded"
SECTION = "wpe"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=1fe8768cbb5fd322f7d50656133549de"
PR = "r0"

require include/wpeframework.inc

# FIXME the compositor shares flags across wpeframework and wpeframework-plugins. Not sure if this is a good idea...
include include/compositor.inc

DEPENDS = " \
    zlib python-jsonref-native virtual/egl \
    ${@bb.utils.contains('DISTRO_FEATURES', 'compositor', '${WPE_COMPOSITOR_DEP}', '', d)} \
"

DEPENDS_append_libc-musl = " libexecinfo"

PV = "3.0+git${SRCPV}"

SRC_URI = "git://github.com/WebPlatformForEmbedded/WPEFramework.git;protocol=git;branch=master \
           file://wpeframework-init \
           file://wpeframework.service.in \
           file://0001-Thread.cpp-Include-limits.h-for-PTHREAD_STACK_MIN-de.patch \
           "
SRCREV = "12f8cff3b0671c55b9f278912079689fd22fa1f2"

inherit cmake pkgconfig systemd update-rc.d

# Yocto root is under /home/root
WPEFRAMEWORK_PERSISTENT_PATH = "/home/root"
WPEFRAMEWORK_SYSTEM_PREFIX = "OE"

PACKAGECONFIG ?= " \
    release \
    ${@bb.utils.contains('DISTRO_FEATURES', 'opencdm', 'opencdm opencdm_gst', '', d)} \
    ${@bb.utils.contains('DISTRO_FEATURES', 'provisioning', 'provisionproxy', '', d)} \
    ${@bb.utils.contains('DISTRO_FEATURES', 'playready_nexus_svp', 'opencdmi_prnx_svp', '', d)} \
    virtualinput websource webkitbrowser \
    "

# add compositor client if Wayland is present
PACKAGECONFIG_append = " ${@bb.utils.contains('DISTRO_FEATURES', 'compositor', 'compositorclient', '', d)}"

# Buildtype
# Maybe we need to couple this to a Yocto feature
PACKAGECONFIG[debug]          = "-DBUILD_TYPE=Debug,,"
PACKAGECONFIG[debugoptimized] = "-DBUILD_TYPE=DebugOptimized,,"
PACKAGECONFIG[releasesymbols] = "-DBUILD_TYPE=ReleaseSymbols,,"
PACKAGECONFIG[release]        = "-DBUILD_TYPE=Release,,"
PACKAGECONFIG[production]     = "-DBUILD_TYPE=Production,,"


PACKAGECONFIG[compositorclient] = "-DCOMPOSITORCLIENT=ON,-DCOMPOSITORCLIENT=OFF"
PACKAGECONFIG[cyclicinspector]  = "-DTEST_CYCLICINSPECTOR=ON,-DTEST_CYCLICINSPECTOR=OFF,"
PACKAGECONFIG[provisionproxy]   = "-DPROVISIONPROXY=ON,-DPROVISIONPROXY=OFF,libprovision"
PACKAGECONFIG[testloader]       = "-DTEST_LOADER=ON,-DTEST_LOADER=OFF,"
PACKAGECONFIG[virtualinput]     = "-DVIRTUALINPUT=ON,-DVIRTUALINPUT=OFF,"

# OCDM
PACKAGECONFIG[opencdm]          = "-DCDMI=ON,-DCDMI=OFF,"
PACKAGECONFIG[opencdm_gst]      = '-DCDMI_ADAPTER_IMPLEMENTATION="gstreamer",-DCDMI=OFF,gstreamer1.0'
PACKAGECONFIG[opencdmi_prnx_svp]= '-DCDMI_BCM_NEXUS_SVP=ON -DCDMI_ADAPTER_IMPLEMENTATION="broadcom-svp",,'

# FIXME
# The WPEFramework also needs limited Plugin info in order to determine what to put in the "resumes" configuration
# it feels a bit the other way around but lets set at least webserver and webkit
PACKAGECONFIG[websource]       = "-DPLUGIN_WEBSERVER=ON,,"
PACKAGECONFIG[webkitbrowser]   = "-DPLUGIN_WEBKITBROWSER=ON,,"

# FIXME, determine this a little smarter
# Provision event is required for libprovision and provision plugin
# Network is provided by the Network control plugin
# Location event is required for locationsync plugin
# Time event is required for timesync plugin
# Identifier event is required for Compositor plugin
# Internet event is provided by the LocationSync plugin
# WebSource event is provided by the WebServer plugin

# Only enable certain events if wpeframework is in distro features
WPEFRAMEWORK_DIST_EVENTS ?= "${@bb.utils.contains('DISTRO_FEATURES', 'thunder', 'Network Time', '', d)}"

WPEFRAMEWORK_EXTERN_EVENTS ?= " \
    ${@bb.utils.contains('PACKAGECONFIG', 'opencdm', 'Decryption', '', d)} \
    ${@bb.utils.contains('PACKAGECONFIG', 'provisionproxy', 'Provisioning', '', d)} \
    ${@bb.utils.contains('PACKAGECONFIG', 'websource', 'WebSource', '', d)} \
    ${WPEFRAMEWORK_DIST_EVENTS} \
    Location Internet \
"

EXTRA_OECMAKE += " \
    -DINSTALL_HEADERS_TO_TARGET=ON \
    -DEXTERN_EVENTS="${WPEFRAMEWORK_EXTERN_EVENTS}" \
    -DBUILD_SHARED_LIBS=ON \
    -DRPC=ON \
    -DBUILD_REFERENCE=${SRCREV} \
    -DTREE_REFERENCE=${SRCREV} \
    -DPERSISTENT_PATH=${WPEFRAMEWORK_PERSISTENT_PATH} \
    -DSYSTEM_PREFIX=${WPEFRAMEWORK_SYSTEM_PREFIX} \
    -DPLUGIN_COMPOSITOR_IMPLEMENTATION=${WPE_COMPOSITOR_IMPL} \
    -DPYTHON_EXECUTABLE=${STAGING_BINDIR_NATIVE}/python-native/python \
"

do_install_append() {
    if ${@bb.utils.contains("DISTRO_FEATURES", "systemd", "true", "false", d)}
    then
        if ${@bb.utils.contains("MACHINE_FEATURES", "platformserver", "true", "false", d)}
        then
           extra_after=""
        elif ${@bb.utils.contains("PREFERRED_PROVIDER_virtual/egl", "broadcom-refsw", "true", "false", d)}
        then
           extra_after="nxserver.service"
        fi
        extra_after="${extra_after} ${WAYLAND_COMPOSITOR}"
        install -d ${D}${systemd_unitdir}/system
        sed -e "s|@EXTRA_AFTER@|${extra_after}|g" < ${WORKDIR}/wpeframework.service.in > ${D}${systemd_unitdir}/system/wpeframework.service
    else
        install -d ${D}${sysconfdir}/init.d
        install -m 0755 ${WORKDIR}/wpeframework-init ${D}${sysconfdir}/init.d/wpeframework
    fi

    if ${@bb.utils.contains("PACKAGECONFIG", "opencdm", "true", "false", d)}
    then
        install -m 0644 ${D}${includedir}/WPEFramework/interfaces/IDRM.h ${D}${includedir}/cdmi.h
    fi
}

SYSTEMD_SERVICE_${PN} = "wpeframework.service"

# ----------------------------------------------------------------------------

PACKAGES =+ "${PN}-initscript"

FILES_${PN}-initscript = "${sysconfdir}/init.d/wpeframework"

FILES_SOLIBSDEV = ""
FILES_${PN} += "${libdir}/*.so ${datadir}/WPEFramework/* ${PKG_CONFIG_DIR}/*.pc"
FILES_${PN} += "${includedir}/cdmi.h"
FILES_${PN}-dev += "${libdir}/cmake/*"

# ----------------------------------------------------------------------------

INITSCRIPT_PACKAGES = "${PN}-initscript"
INITSCRIPT_NAME_${PN}-initscript = "wpeframework"

# If WPE Framework is enabled as distro feature, start earlier. Assuming packagegroup-wpe-boot is used and we're in control for the network
WPEFRAMEWORK_START = "${@bb.utils.contains('DISTRO_FEATURES', 'wpeframework', '40', '80', d)}"

INITSCRIPT_PARAMS_${PN}-initscript = "defaults ${WPEFRAMEWORK_START} 24"

RRECOMMENDS_${PN} = "${PN}-initscript"

# ----------------------------------------------------------------------------

INSANE_SKIP_${PN} += "dev-so"
INSANE_SKIP_${PN}-dbg += "dev-so"

# ----------------------------------------------------------------------------

RDEPENDS_${PN}_rpi = "userland"

# Avoid settings ADNEEDED in LDFLAGS as this can cause the libcompositor.so to drop linking to libEGL/libGLES
# which might not be needed at first glance but will cause problems higher up in the change, there for lets drop -Wl,--as-needed
# some distros, like POKY (morty) enable --as-needed by default (e.g. https://git.yoctoproject.org/cgit/cgit.cgi/poky/tree/meta/conf/distro/include/as-needed.inc?h=morty)
ASNEEDED = ""
